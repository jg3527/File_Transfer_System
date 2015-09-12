import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;
//TODO to check is close that simple?
//Restrict linkup
public class DV {
	static Node node;
	static Map<Node, Neighbor> neighbors;
	static DatagramSocket socket;
	static int timeout;
	static int selfPort;
	static String selfIP;
	static Map<Node, Integer> heartBeat;
	static HashMap<Node, Double> originCosts;
	static int ack;
	static String saveFilePath;
	static File f;
	static HashMap<Node, Node> proxy;//<realneighbor, proxy>
	public static void main(String[] args) throws SocketException{
		Runtime.getRuntime().addShutdownHook(new ShutDownWork());  
		originCosts = new HashMap<Node, Double>();
		neighbors = Collections.synchronizedMap(new HashMap<Node, Neighbor>());
		heartBeat = Collections.synchronizedMap(new HashMap<Node, Integer>());
		proxy = new HashMap<Node, Node>();
		saveFilePath = MyProtocol.RVDFileDir;
		f = new File(DV.saveFilePath + "tmp");
		InetAddress addr;
		ack = 0;
		try {
			addr = InetAddress.getLocalHost();
			String ip=addr.getHostAddress().toString();
			selfIP = ip;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		//String file = MyProtocol.file;
		//initializeFile(file);
		initializeFile(args[0]);
		node = new Node(selfIP, selfPort);
		try{
			new UserInputListener().start();
			new SendDVThread(timeout).start();
			new SocketListener(selfPort).start();
			new ReceiveListener(timeout).start();	
		}catch(Exception e){
			System.exit(-1);
		}
	}
	//The format of distance vector is sourceIP;portNumber;desIP:portNumber d(desIP);desIP:portNum d(desIP);
	static void BFA(String vector){

		String[] str = vector.split(";");
		String ip = str[0];
		int pNum = Integer.parseInt(str[1]);
		HashMap<Node, Double> map = new HashMap<Node, Double>();
		if(2 >= str.length)
			return;
		for(int i = 2; i < str.length - 1; i++){
			Debug.print("str(i = + " + i + "): " + str[i]);
			String[] tmp = str[i].split(" ");
			String[] dest = tmp[0].split(":");
			if(tmp.length != 2 || dest.length != 2)
				continue;
			Double cost = Double.parseDouble(tmp[1]);
			map.put(new Node(dest[0], Integer.parseInt(dest[1])), cost);
			Debug.print("map add port " + dest[1]);
		}

		Node thisNode = new Node(ip, pNum);
		if(!map.containsKey(node)){
			return;
		}
		double c = map.get(node);
		if(!neighbors.containsKey(thisNode)){
			Neighbor n = new Neighbor(thisNode, c);
			n.setDistanceVector(map);
			neighbors.put(thisNode, n);
		}else{
			Neighbor n = neighbors.get(thisNode);
			n.setDistanceVector(map);
			Debug.print("keke" + " changenode " + n.getNode().getPortNumber());	
		}
		Debug.print("Set @@@@@" + pNum + " heart beat " + 0);
		heartBeat.put(thisNode, 0);
		//upadate self dv
		updateDV(map, thisNode);

	}
	static void updateDV(HashMap<Node, Double> map, Node thisNode){
		boolean  m = false;
		Debug.print("UpdateDV " + selfIP + " selfPort " + selfPort);
		double c = map.get(new Node(selfIP, selfPort)); // this is the shortest path length from self to this neighbor
		Debug.print("Aha, me -> this neighbor(" + thisNode.getPortNumber() + ") weight: " + c);
		Neighbor n = neighbors.get(thisNode);		
		/**/
		for(Node node : map.keySet()){
			double newCost = map.get(node) + c;
			if(neighbors.containsKey(node)){
				double old = neighbors.get(node).getMinPathLength();
				if(newCost < old){
					m = true;
					Debug.print("Changing cost:" + newCost);
					neighbors.get(node).setMinPathLength(newCost);
					neighbors.get(node).setPreNode(thisNode);
				}else if(newCost == old){
					//DO nothing
				}else if(neighbors.get(node).getPreNode().equals(thisNode)){
					m = true;
					if(newCost > neighbors.get(node).getDirLinkCost()){
						neighbors.get(node).setMinPathLength(neighbors.get(node).getDirLinkCost());
						neighbors.get(node).setPreNode(node);
					}else{
						Debug.print("Changing cost:" + newCost);
						neighbors.get(node).setMinPathLength(newCost);
						neighbors.get(node).setPreNode(thisNode);
					}
				}
			}else if(!node.equals(DV.node)){
				Neighbor newNei = new Neighbor(node, Double.POSITIVE_INFINITY, newCost);
				newNei.setPreNode(thisNode);
				m=true;
				neighbors.put(node, newNei);
			}
		
		}

				
		
		
		if(m){
			
			Debug.print("Befor sendout " + n.getMinPathLength());
			sendNeighborsDV();
		}
	}
	static void sendOutMessage(String str, String IP, int port){

		DatagramSocket clientSocket;
		try {
			clientSocket = new DatagramSocket();
			DatagramPacket sendPacket;
			sendPacket = new DatagramPacket(str.getBytes(), str.length(), InetAddress.getByName(IP), port);
			clientSocket.send(sendPacket);
			if(clientSocket != null)
				clientSocket.close();
			Debug.print("Send " + port + " : " + str);
		} catch (UnknownHostException e) {
			System.out.println("IP address is not valid.");
			e.printStackTrace();
		} catch(SocketException e){
			System.out.println("Failed to initialize the socket.");
		} catch(IOException e){
			System.out.println("Error occurred when try to send out the packet.");
		}
	}
	static void sendOutDV(Node node){
		Debug.print("Sending out distance vector.");
		String str = selfIP + ";" + selfPort + ";";
		for(Node n: neighbors.keySet()){
			//if(neighbors.get(n).getDirLinkCost() != Double.POSITIVE_INFINITY){
				if(neighbors.get(n).getPreNode().equals(node) && !n.equals(node)){
					str += n.getIP() + ":" + n.getPortNumber() + " " + Double.POSITIVE_INFINITY + ";";
				}else{
					str += n.getIP() + ":" + n.getPortNumber() + " " + neighbors.get(n).getMinPathLength() + ";";
				}
			//}
		}
		sendOutMessage("DV;" + str, node.getIP(), node.getPortNumber());

	}
	static void sendNeighborsDV(){
		Set<Node> set =  neighbors.keySet();
		//Map<Node, Integer> tmp = heartBeat;
		for(Node node: set){
			if(neighbors.get(node).getDirLinkCost() != Double.POSITIVE_INFINITY)
			sendOutDV(node);
		}
	}
	//Told this neighbor is down
	static void setNeighborInfinite(Neighbor n){
		if(heartBeat.containsKey(n.getNode())){		
			heartBeat.remove(n.getNode());
			Debug.print("Linkdown set infinite delete from heartbeat");
		}
		n.setDirLinkCost(Double.POSITIVE_INFINITY);
		if(n.getPreNode().equals(n.getNode())){
			n.setMinPathLength(Double.POSITIVE_INFINITY);
		}
		Debug.print("setNeighborInfinite: minpath: " + n.getMinPathLength());
		//n.setDistanceVector(null);
		for(Neighbor tmp: neighbors.values()){
			if(tmp.getPreNode().equals(n.getNode())){
				tmp.setMinPathLength(tmp.getDirLinkCost());
				tmp.setPreNode(tmp.getNode());
				Debug.print("Set infinite");
				//showTable();//TODO 
			}
		}
		//reCalculateDV(n);
		sendNeighborsDV();
	}
	static void reCalculateDV(Neighbor nei){
		for(Neighbor n: neighbors.values()){
			if(n.equals(nei))
				continue;
			if(n.getMinPathLength() != Double.POSITIVE_INFINITY){
				updateDV(n.getDistanceVector(), n.getNode());
			}
		}
	}
	static void close(){
		System.exit(-1);
	}
	// self node -> node cost change to the new cost.
	static void changeCost(Node node, double cost, boolean fromUser){
		Neighbor nei = neighbors.get(node);
		double change = cost - nei.getDirLinkCost();
		nei.setDirLinkCost(cost);

		if(nei.getPreNode().equals(node)){
			nei.setMinPathLength(cost);
		}else if(cost < nei.getMinPathLength()){
			nei.setMinPathLength(cost);
			nei.setPreNode(node);
		}

		for(Neighbor tmp: neighbors.values()){
			if(tmp.getPreNode().equals(node) && !tmp.getNode().equals(node)){

				double newCost = tmp.getMinPathLength() + change;
				if(newCost > tmp.getDirLinkCost()){
					tmp.setMinPathLength(tmp.getDirLinkCost());
					tmp.setPreNode(tmp.getNode());
				}else{
					Debug.print("!!!!!!!!!!!!changed!!" + tmp.getNode().getPortNumber() + " new cost: " +  newCost + " change:" + change);
					tmp.setMinPathLength(newCost);
				}
			}
		}
		if(fromUser){
			String message = MyProtocol.CHANGECOST + ";" + selfIP + ";" + selfPort + ";" + cost + ";";
			sendOutMessage(message,node.getIP() , node.getPortNumber());
		}
		sendNeighborsDV();
	}
	/*	private void reCalculateDVAfterChangedToInfinite(Neighbor n){
		for(Node node: neighbors.keySet()){
			Neighbor tmp = neighbors.get(node);
			if(tmp.getPreNode().equals(node)){
				tmp.setPreNode(node);
				tmp.setMinPathLength(tmp.getDirLinkCost());
			}
		}
	}*/
	static void linkDown(String IP, int port, Boolean fromUser){
		Node ld = new Node(IP, port);
		if(neighbors.containsKey(ld)){
			setNeighborInfinite(neighbors.get(ld));
			if(fromUser){
				String message = "LINKDOWN;" + selfIP + ";" + selfPort + ";";
				sendOutMessage(message, IP, port);
			}
			System.out.println("Linkdown finished.");
		}else{
			System.out.println("There is no such link");
		}
	}
	static void linkUp(String IP, int port, boolean fromUser){
		//send messsage to ip
		// update neighbor
		// update distance vector
		Node n = new Node(IP, port);

		if(originCosts.containsKey(n)){
			double oc = originCosts.get(n);
			changeCost(n, oc, fromUser);
			heartBeat.put(n, 0);
			System.out.println("Linkup finished.");
		}else{
			System.out.println("Not valid linkup command.");
		}
		if(fromUser){
			String message = "LINKUP;" + selfIP + ";" + selfPort + ";";
			sendOutMessage(message, IP, port);
		}
	}
	static void showTable(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String currentTime = dateFormat.format(date); //2014/08/06 15:59:48
		System.out.println(currentTime + " Distance vector list is:");
		for(Node node: neighbors.keySet()){
			String destIP = node.getIP();
			int port = node.getPortNumber();
			Neighbor n = neighbors.get(node);
			double cost = n.getMinPathLength();
			String link = n.getPreNode().getIP() + ":" + n.getPreNode().getPortNumber();
			String info = "Destination = " + destIP + ":" + port + ", Cost = " + cost + ", Link = "
					+ "(" + link +")";					
			System.out.println(info);
		}
	}
	private static void initializeFile(String filePath){
		File file = new File(filePath);
		InputStreamReader read;
		try {
			read = new InputStreamReader(new FileInputStream(file));	
			BufferedReader bufferedReader = new BufferedReader(read);	
			String line;
			try {
				line = bufferedReader.readLine();
				String[] tmp;
				if(line != null){
					tmp = line.split(" ");
					selfPort = Integer.parseInt(tmp[0]);
					timeout = Integer.parseInt(tmp[1]) * 1000;
					while((line = bufferedReader.readLine()) != null && !(line = line.trim()).equals("")){
						tmp = line.split(":");
						String[] portAndWeight = tmp[1].split(" ");
						String IP = tmp[0];
						int port = Integer.parseInt(portAndWeight[0]);
						Double weight = Double.parseDouble(portAndWeight[1]);
						Node node = new Node(IP, port);
						originCosts.put(node, weight);
						Neighbor n = new Neighbor(node, weight);
						neighbors.put(node, n);
						heartBeat.put(node, 0);
					}
					bufferedReader.close();
					read.close();		
				}else{
					System.out.println("The file is not valid!");
				}
			} catch (IOException e) {
				System.out.println("Error occured while reading the file.");
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			System.out.println("Can not find the file.");
			e.printStackTrace();
		}
		sendNeighborsDV();

	}
	static void transferFile(String path){

	}
}
// heartbeat
class SendDVThread extends Thread{
	private int timeout;
	private Timer timer;
	public SendDVThread(int t){
		this.timeout = t;
		this.timer = new Timer();
	}
	public void run(){
		TimerTask sendDV = new TimerTask(){
			public void run(){
				Debug.print("Heartbeat tell neighbors I am alive.");
				DV.sendNeighborsDV();
			}
		};
		timer.schedule(sendDV, 0, timeout);	
	}
}
class ReceiveListener extends Thread {
	private int timeout;
	private Timer timer;
	public ReceiveListener(int t){
		this.timeout = t;
		this.timer = new Timer();
	}
	@Override
	public void run(){
		TimerTask heartBeat = new TimerTask(){
			@Override
			public void run() {
				List<Node> l = new ArrayList<Node>();
				for(Node n: DV.heartBeat.keySet()){

					int t = DV.heartBeat.get(n) + 1;
					DV.heartBeat.put(n, t);
					Debug.print("Heart beat " + n.getPortNumber() + " time: " + t);
					if(t > 3)
					{
						Debug.print(n.getPortNumber() + " is Dead. Checked by heartbeat");
						l.add(n);

					}	
				}
				for(Node tn: l){
					DV.heartBeat.remove(tn);
					DV.neighbors.get(tn).setDirLinkCost(Double.POSITIVE_INFINITY);
					DV.setNeighborInfinite(DV.neighbors.get(tn));
				}
			}	
		};
		timer.schedule(heartBeat, 0, timeout);
	}
}
class ShutDownWork extends Thread {
	public void run(){
		if(DV.socket != null)
			DV.socket.close();
		if(Transfer.send != null){
			Transfer.send.close();			
		}
		Debug.print("Shutdown");
	}
}
class UserInputListener extends Thread{
	BufferedReader inFromUser;
	public UserInputListener(){
		inFromUser = new BufferedReader(new InputStreamReader(System.in));
	}
	public void run(){
		String command;
		Debug.print("UserInputListenerStarts");
		try {
			while((command = inFromUser.readLine()) != null){
				String oricmd = command;
				command = command.toUpperCase().trim();
				if(command.startsWith(MyProtocol.SHOWRT)){
					DV.showTable();
				}else if(command.startsWith(MyProtocol.LINKDOWN)){
					String tmp[] = command.split(" ");
					if(tmp.length < 3){
						System.out.println("Unvalid linkdown command. You need to specify the IP and the port number.");
						continue;
					}
					int tmpP ;
					try{
						tmpP = Integer.parseInt(tmp[2]);
					}catch(Exception e){
						System.out.println("Unvalid Command!");
						continue;
					}
					DV.linkDown(tmp[1], tmpP, true);
				}else if(command.startsWith(MyProtocol.LINKUP)){
					String tmp[] = command.split(" ");
					if(tmp.length < 3){
						System.out.println("Unvalid linkup command. You need to specify the IP and the port number.");
						continue;
					}
					int tmpP;
					try{
						tmpP = Integer.parseInt(tmp[2]);
					}catch(Exception e){
						System.out.println("Unvalid Command!");
						continue;
					}
					DV.linkUp(tmp[1], tmpP, true);
				}else if(command.startsWith(MyProtocol.CHANGECOST)){
					String tmp[] = command.split(" ");
					if(tmp.length != 4){
						System.out.println("Unvalid command.");
						continue;
					}
					int tmpP, tmpC;
					try{
						tmpP = Integer.parseInt(tmp[2]);
						tmpC = Integer.parseInt(tmp[3]);
					}catch(Exception e){
						System.out.println("Invaild input!");
						continue;
					}
					Node tmpN = new Node(tmp[1], tmpP);
					DV.changeCost(tmpN, tmpC,  true);
				}else if(command.startsWith(MyProtocol.CLOSE)){
					DV.close();
				}else if(command.startsWith(MyProtocol.TRANSFER)){
					// transfer filename destIP destPort
					String[] info = oricmd.split(" ");
					if(info.length != 4){
						System.out.println("Unvalid command.");
						continue;
					}
					Transfer trans = new Transfer(info[2], Integer.parseInt(info[3]), info[1]);
					trans.start();
				}else if(command.startsWith(MyProtocol.ADDPROXY)){
					String[] info = command.split(" ");
					if(info.length != 5){
						System.out.println("Unvalid command.");
						continue;
					}
					Node p = new Node(info[1], Integer.parseInt(info[2]));
					Node nei = new Node(info[3], Integer.parseInt(info[4]));
					if(DV.neighbors.keySet().contains(nei) && DV.neighbors.get(nei).getMinPathLength() != Double.POSITIVE_INFINITY){
						DV.proxy.put(nei, p);
					}else{
						System.out.println("Unvalid command.");
					}
				}else if(command.startsWith(MyProtocol.REMOVEPROXY)){
					String[] info = command.split(" ");
					if(info.length != 3){
						System.out.println("Unvalid command.");
						continue;
					}
					Node nei = new Node(info[1], Integer.parseInt(info[2]));
					if(DV.proxy.containsKey(nei)){
						DV.proxy.remove(nei);
					}else{
						Debug.print("Does not contain!");
					}
				}
				else{
					System.out.println("Invaild input!");
				}
			}
		} catch (IOException e) {
			Debug.print("Listening to the user's input failed");
			e.printStackTrace();
		}
	}
}
class SocketListener extends Thread {
	DatagramSocket socket;
	BufferedOutputStream bos;
	public SocketListener(int portN) throws SocketException{
		//try {
		socket = new DatagramSocket(portN);
		/*} catch (SocketException e) {
			System.out.println("Initialize socket failed");
			e.printStackTrace();
		}*/
	}
	@Override
	public void run(){

		while(true)
		{
			DatagramPacket receivePacket;
			byte[] receiveData;
			try {
				receiveData = new byte[MyProtocol.FM];
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				socket.receive(receivePacket);
			} catch (IOException e) {
				Debug.print("Received something weird.");
				continue;
			}

			String message = new String(receivePacket.getData());
			byte[] dataPkg = receivePacket.getData();
			if(!message.startsWith(MyProtocol.TRANSFER))
				Debug.print("Received message: " + message);
			else{
				Debug.print("Received transfer message");
			}
			String firstPart = message.split(";")[0].toUpperCase();
			int index = message.indexOf(";") + 1;
			if(firstPart.startsWith(MyProtocol.DV)){
				//Starts with DV
				String vector = message.substring(index);
				//vector format: sourceIP; portNdesIP d(desIP);desIP d(desIP);
				Debug.print("RECEIVED DV: " + vector);
				DV.BFA(vector);
			}else if(firstPart.startsWith(MyProtocol.LINKDOWN)){
				// This kind of format is LinkDown;destIP;portNumber
				String[] info = message.substring(index).split(";");
				Debug.print(info[1]);
				DV.linkDown(info[0], Integer.parseInt(info[1]), false);
			}else if(firstPart.startsWith(MyProtocol.LINKUP)){
				String[] info = message.substring(index).split(";");
				DV.linkUp(info[0], Integer.parseInt(info[1]), false);
			}else if(firstPart.startsWith(MyProtocol.CHANGECOST)){
				String info[] = message.split(";");
				DV.changeCost(new Node(info[1], Integer.parseInt(info[2])), Double.parseDouble(info[3]), false);
			}else if(firstPart.startsWith(MyProtocol.TRANSFER)){
				Debug.print("Recived transfer message. else if.");
				InetAddress source = receivePacket.getAddress();
				int portNumber = receivePacket.getPort();
				fileTransfer(source, portNumber, dataPkg);
			}else if(firstPart.startsWith(MyProtocol.END)){
				Debug.print("Received end message");
				bos = null;
				InetAddress source = receivePacket.getAddress();
				int portNumber = receivePacket.getPort();
				try {
					fileTransferEnd(source.getHostAddress(), portNumber, dataPkg);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}
	private static void copyFile(String oldF, String newF){
		try{      
			int byteread = 0;    
			File oldfile = new File(oldF);    
			if (oldfile.exists()) {  
				InputStream inStream = new FileInputStream(oldF); 
				FileOutputStream fs = new FileOutputStream(newF);    
				byte[] buffer = new byte[1];    
			   
				while ( (byteread = inStream.read(buffer)) != -1) {        
					fs.write(buffer, 0, byteread);    
				}    
				fs.close();
				inStream.close();    
			}    
		}    
		catch (Exception e) {    
			System.out.println( "error  ");  
			e.printStackTrace();    
		}    
	}
	public void fileTransferEnd(String preIP, int prePort, byte[] pkg) throws IOException{

		EndPackage end = new EndPackage(pkg);

		if(end.vaild()){
			System.out.println("Packet received");
			System.out.println("Source = " + end.getSourceIP() + ":" + end.getSPort());
			System.out.println("Destination = " + end.getdestIP() + ":" + end.getDPort());
			if(new Node(end.getdestIP(), end.getDPort()).equals(DV.node)){
				String fileName = end.getFileName();
				Debug.print(fileName + "Receiver filename");
				//Debug.print("jpg equal? " + fileName.equals("jg3527.jpg"));
				File finalFile = new File(DV.saveFilePath + fileName);
				//finalFile.createNewFile();
				boolean rename = DV.f.renameTo(finalFile);
				Debug.print("Final fileName: " + finalFile + "((" + DV.saveFilePath + " sfp " + fileName);
				Debug.print("Successful rename? " + rename);

				finalFile.createNewFile();

				copyFile(DV.saveFilePath + "tmp", DV.saveFilePath + fileName);
				//DV.f.delete();
				System.out.println("File received successfully");
				if(bos != null)
					try {
						bos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
			}else{ 
				String nextIp;
				int nextPort;
				Node destNode = new Node(end.getdestIP(), end.getDPort());
				if(DV.proxy.containsKey(destNode)){
					nextIp = DV.proxy.get(destNode).getIP();
					nextPort = DV.proxy.get(destNode).getPortNumber();
				}else{
					nextIp = DV.neighbors.get(destNode).getPreNode().getIP();
					nextPort =  DV.neighbors.get(destNode).getPreNode().getPortNumber();
				}
				System.out.println("Next hop = " + nextIp + ":" + nextPort);
				Transfer t = new Transfer(nextIp, nextPort, pkg);
				t.start();
			}
			String ackMsg = "ACK";
			DV.sendOutMessage(ackMsg, preIP, prePort);
			Debug.print("Send: " + prePort + "  end ack");
		}else{
			Debug.print("End package checksum not valid.");
		}
	}
	public void fileTransfer(InetAddress preAddr, int prePort, byte[] dataPkg){
	
		//byte[] pkg = new byte[dataPkg.length - 8];
		//System.arraycopy(dataPkg, 8, pkg, 0, dataPkg.length - 8);
		InetAddress source = preAddr;//repkg.getAddress();
		int portNumber = prePort;//repkg.getPort();

		try {

			Header h = new Header(dataPkg);
			Debug.print(h.vaild() +"");
			if(h.vaild()){

				System.out.println("Packet received");
				System.out.println("Source = " + h.getSourceIP() + ":" + h.getSPort());
				System.out.println("Destination = " + h.getdestIP() + ":" + h.getDPort());
				Node destNode = new Node(h.getdestIP(), h.getDPort());
				if(destNode.equals(DV.node)){
					// sava data
					if(bos == null){
						bos = new BufferedOutputStream(new FileOutputStream(DV.f));
					}
					Debug.print("Saving data");
					bos.write(h.getData(), 0, h.getData().length);
					bos.flush();
					System.out.println("File fragment reaches the destination.");
				}else{
					String nextIp;
					int nextPort;
					if(DV.proxy.containsKey(destNode)){
						nextIp = DV.proxy.get(destNode).getIP();
						nextPort = DV.proxy.get(destNode).getPortNumber();
					}else{
						nextIp = DV.neighbors.get(destNode).getPreNode().getIP();
						nextPort =  DV.neighbors.get(destNode).getPreNode().getPortNumber();
					}
					System.out.println("Next hop = " + nextIp + ":" + nextPort);
					Transfer t = new Transfer(nextIp, nextPort, dataPkg);
					t.start();
				}
				String ackMsg = "ACK";
				DV.sendOutMessage(ackMsg, source.getHostAddress(), portNumber);
				Debug.print("Send: " + portNumber + " normal ack");
			}else{
				Debug.print("header is not valid in funtion fileTransfer.");
			}
		}catch (Exception e) {
			e.printStackTrace();
			return;

		}
		
	}

}
class EndPackage{
	private byte[] checksum;
	private byte[] sourceIp;
	private byte[] sourcePort;
	private byte[] destIp;
	private byte[] destPort;
	private byte[] fileNameByte;
	private byte[] secondPart;
	private byte[] fileNameLen;
	public EndPackage(byte[] pkg){
		checksum = new byte[8];
		sourceIp = new byte[12];
		sourcePort = new byte[4];
		destIp = new byte[12];
		destPort = new byte[4];
		secondPart = new byte[pkg.length - 11];
		fileNameLen = new byte[4];
		fileNameByte = new byte[pkg.length - 47];
		System.arraycopy(pkg, 3, checksum, 0, 8);
		System.arraycopy(pkg, 11, destIp, 0, 12);
		System.arraycopy(pkg, 23, destPort, 0, 4);
		System.arraycopy(pkg, 27, sourceIp, 0, 12);
		System.arraycopy(pkg, 39, sourcePort, 0, 4);
		System.arraycopy(pkg, 43, fileNameLen, 0, 4);
		System.arraycopy(pkg, 47, fileNameByte, 0, pkg.length - 47);
		System.arraycopy(pkg, 11, secondPart, 0, pkg.length - 11);
	}
	public boolean vaild() throws IOException{
		Debug.print("Valid: " + this.getdestIP() + ":" + this.getDPort() + ";Source: " + this.getSourceIP() + ":" + this.getDPort());
		CRC32 crc = new CRC32();
		crc.update(secondPart);
		try {
			if(crc.getValue() == getChecksum()){
				return true;
			}else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public String getFileName() throws IOException{
		Debug.print("getfilenamelen: " + this.getFileNameLen());
		byte[] fileb = new byte[this.getFileNameLen()];
		System.arraycopy(fileNameByte, 0, fileb, 0, this.getFileNameLen());
		return new String(fileb);
	}
	public long getChecksum() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(checksum);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readLong();
	}
	public String getSourceIP(){
		return new String(sourceIp);
	}
	public String getdestIP(){
		return new String(destIp);
	}
	public int getSPort() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(sourcePort);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
	public int getFileNameLen()throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(fileNameLen);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
	public int getDPort() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(destPort);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
}
class Header{
	private byte[] checksum;
	private byte[] sourceIp;
	private byte[] sourcePort;
	private byte[] destIp;
	private byte[] destPort;
	private byte[] secondpart;
	private byte[] len;
	private byte[] pkg;
	public Header(byte[] pkg){
		this.pkg = pkg;
		checksum = new byte[8];
		sourceIp = new byte[12];
		sourcePort = new byte[4];
		destIp = new byte[12];
		destPort = new byte[4];
		len = new byte[4];
		secondpart = new byte[pkg.length - 16];
		System.arraycopy(pkg, 8, checksum, 0, 8);
		System.arraycopy(pkg, 16, destIp, 0, 12);
		System.arraycopy(pkg, 28, destPort, 0, 4);
		System.arraycopy(pkg, 32, sourceIp, 0, 12);
		System.arraycopy(pkg, 44, sourcePort, 0, 4);
		System.arraycopy(pkg, 48, len, 0, 4);
		System.arraycopy(pkg, 16, secondpart, 0, pkg.length - 16);
	}
	public String getSourceIP(){
		return new String(sourceIp);
	}
	public int getLen()throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(len);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
	public String getdestIP(){
		return new String(destIp);
	}
	public int getSPort() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(sourcePort);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
	public int getDPort() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(destPort);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readInt(); 
	}
	public long getChecksum() throws IOException{
		ByteArrayInputStream bais=new ByteArrayInputStream(checksum);  
		DataInputStream dis=new DataInputStream(bais);  
		return  dis.readLong();
	}
	public byte[] getData() throws IOException{
		byte[] data = new byte[this.getLen()];
		System.arraycopy(pkg, 52, data, 0, this.getLen());
		return data;
	}
	public boolean vaild() throws IOException{
		CRC32 crc = new CRC32();
		crc.update(secondpart);
		Debug.print("reciver: cal : " + crc.getValue() + " pkg : " + getChecksum());
		Debug.print("reciver: cal : " + crc.getValue() + " pkg : " + getChecksum());
		try {
			if(crc.getValue() == getChecksum()){
				return true;
			}else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;

		}

	}

}
class Node{
	private String IP;
	private Integer portNumber;
	public Node(String iP, int port){
		this.setIP(iP);
		this.setPortNumber(port);
	}
	public int hashCode(){  
		int result = 0;  
		result = 31 * result + ((IP == null) ? 0 : IP.hashCode());  
		result = 31 * result + ((portNumber == null) ? 0 : portNumber.hashCode());  
		return result;  

	}
	public boolean equals(Object n){
		if(n == null || n.getClass() != this.getClass())
			return false;
		Node cur = (Node) n;
		if( cur.getPortNumber()== portNumber && cur.getIP().equals(IP)){
			return true;
		}
		return false;
	}
	public String getIP() {
		return IP;
	}
	public void setIP(String iP) {
		IP = iP;
	}
	public int getPortNumber() {
		return portNumber;
	}
	public void setPortNumber(int portNumber) {
		this.portNumber = portNumber;
	}


}

class Neighbor{
	private Node node;
	private double dirLinkCost;
	private double minPathLength;
	private Node preNode;
	private HashMap<Node, Double> distanceVector;
	public Neighbor(Node n){
		setNode(n);
		setMinPathLength(Double.POSITIVE_INFINITY);
	}

	public Neighbor(Node n, double cost){
		setNode(n);
		setDirLinkCost(cost);
		setMinPathLength(cost);
		setPreNode(n);
	}
	public Neighbor(Node n, double cost, double minPathLen){
		setNode(n);
		setDirLinkCost(cost);
		setMinPathLength(minPathLen);
		setPreNode(n);
	}

	public double getDirLinkCost() {
		return dirLinkCost;
	}

	public void setDirLinkCost(double dirLinkCost) {
		this.dirLinkCost = dirLinkCost;
	}

	public double getMinPathLength() {
		return minPathLength;
	}

	public void setMinPathLength(double minPathLength) {
		this.minPathLength = minPathLength;
	}

	public Node getPreNode() {
		return preNode;
	}

	public void setPreNode(Node preN) {
		this.preNode = preN;
	}

	public HashMap<Node, Double> getDistanceVector() {
		return distanceVector;
	}

	public void setDistanceVector(HashMap<Node, Double> distanceVector) {
		this.distanceVector = distanceVector;
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}
	public boolean equals(Object n){
		if(n == null || n.getClass() != this.getClass())
			return false;
		Neighbor cur = (Neighbor) n;
		if( cur.getNode().equals(this.getNode())){
			return true;
		}
		return false;
	}
	public int hashCode(){
		return this.getNode().hashCode();
	}
}
