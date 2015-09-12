import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.zip.CRC32;
public class Transfer extends Thread {
	static DatagramSocket send;
	private DatagramPacket ackPkg;
	private int destPort;
	private int selfPort;
	private String path;
	private String destIP;
	private byte[] head;
	private byte[] dataPkg;
	private String nextIP;
	private int nextPort;
	private boolean startPoint;
	public Transfer(String destIP, int destPort, String path) throws SocketException {
		this.destPort = destPort;
		this.selfPort = DV.selfPort + 1000;
		this.path = path;
		this.destIP = destIP;
		this.head = this.creatHead();
		try{
			if(send == null)
				send = new DatagramSocket(selfPort);
		}catch(Exception  e){
			send = new DatagramSocket(selfPort + 200);
		}
		this.startPoint = true;
		Node dest = new Node(destIP, destPort);
		if(DV.proxy.containsKey(dest)){
			nextIP = DV.proxy.get(dest).getIP();
			nextPort = DV.proxy.get(dest).getPortNumber();
		}else if(DV.neighbors.containsKey(dest)){
			nextIP = DV.neighbors.get(dest).getPreNode().getIP();
			nextPort =  DV.neighbors.get(dest).getPreNode().getPortNumber();
		}else{
			System.out.println("Unreachable neighbor.");
			return;
		}
	}


	public Transfer(String nextIP, int nextPort, byte[] pkg) throws SocketException{
		this.nextIP = nextIP;
		this.nextPort = nextPort;
		this.dataPkg = pkg;
		this.selfPort = DV.selfPort + 1000;
		this.startPoint = false;
		try {
			if(send == null){
				send = new DatagramSocket(selfPort);
				}
		} catch (SocketException e) {
			send = new DatagramSocket(selfPort + 1000); 
			e.printStackTrace();
		}
	}
	public void pass() throws Exception{
		byte[] ackBuf = new byte[1024];
		ackPkg = new DatagramPacket(ackBuf, ackBuf.length);
		Debug.print("Passing this pkg to next hop.");
		DatagramPacket pkg = new DatagramPacket(dataPkg, dataPkg.length, new InetSocketAddress(nextIP, nextPort));
		
		while (true) {
			try{
			Debug.print("Send next hop the package");
			send.setSoTimeout(MyProtocol.FM);
			send.send(pkg);
			send.receive(ackPkg);
			Debug.print("Next hop ack" + new String(ackPkg.getData()));
			break;
			}catch(SocketTimeoutException e){
				Debug.print("Time out. Resend.");
				byte[] tmp = new byte[48];
				Debug.print("Retransfer: " + new String(tmp));
				continue;
			}
		}

	}
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getIp() {
		return destIP;
	}

	public void setIp(String destIP) {
		this.destIP = destIP;
	}

	public void send() throws IOException {
		BufferedInputStream bis = new BufferedInputStream(
				new FileInputStream(new File(path)));
		try {
			//The ack message
			byte[] ackBuf = new byte[1024];
			ackPkg = new DatagramPacket(ackBuf, ackBuf.length);
			//The file date
			byte[] dataBuf = new byte[MyProtocol.MAX];
			Node dest = new Node(destIP, destPort);
			String nextIp;
			int nextPort;
			if(DV.proxy.containsKey(dest)){
				nextIp = DV.proxy.get(dest).getIP();
				nextPort = DV.proxy.get(dest).getPortNumber();
			}else if(DV.neighbors.containsKey(dest)){
				nextIp = DV.neighbors.get(dest).getPreNode().getIP();
				nextPort =  DV.neighbors.get(dest).getPreNode().getPortNumber();
			}else{
				System.out.println("Unreachable neighbor.");
				bis.close();
				return;
			}
			int len;
			while ((len = bis.read(dataBuf)) != -1) {
				//Datagram format(total head length = 48): "transfer"(8byte) + checksum(8) + DestIP(12) + DestPort(4) + sourceIP(12) + sourcePort(4) + data(MAX) ; 
				Debug.print("Sender len: " + len);
				CRC32 crc = new CRC32();
				byte[] tb = concat(intBytes(len), dataBuf);
				byte[] secPart = concat(head, tb);
				crc.update(secPart);
				byte[] checksum = longBytes(crc.getValue());
				byte[] tmp = concat(checksum, secPart);
				byte[] finalPkg = concat("TRANSFER".getBytes(), tmp);
				
				DatagramPacket pkg = new DatagramPacket(finalPkg, finalPkg.length, new InetSocketAddress(nextIp, nextPort));
				
				while (true) {
					try{
					Debug.print(crc.getValue() + " Send checksum.");
					Debug.print(finalPkg.length + " final pkg length");
					byte[] cex = new byte[finalPkg.length - 16];
					System.arraycopy(finalPkg, 16, cex, 0, finalPkg.length - 16);
					CRC32 k = new CRC32();
					k.update(cex);
					Debug.print("Calculated check: " + k.getValue());
					send.setSoTimeout(MyProtocol.FM);
					send.send(pkg);
					send.receive(ackPkg);
					Debug.print(new String(ackPkg.getData()));
					break;
					}catch(SocketTimeoutException e){
						Debug.print("Time out. Resend.");
						continue;
					}
				}
				dataBuf = new byte[MyProtocol.MAX];
			try {
					
					this.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			String[] fileInfo = path.split("/");
			String fileName = fileInfo[fileInfo.length - 1];
			int fLen = fileName.getBytes().length;
			// ENd(3byte) + checksum + destIP + destPort + sourceIp + sourcePort + filename length(int 4byte) + filename
			byte[] flenb = intBytes(fLen);
			byte[] tt = concat(flenb, fileName.getBytes());
			byte[] tendpkgsec = concat(head, tt);
			byte[] endpkgsec = new byte[MyProtocol.FM - 11];
			System.arraycopy(tendpkgsec, 0, endpkgsec, 0, tendpkgsec.length);
			CRC32 crc = new CRC32();
			crc.update(endpkgsec);
			byte[] endchecksum = longBytes(crc.getValue());
			byte[] tmp = concat(endchecksum, endpkgsec);
			dataBuf = new byte[MyProtocol.FM];
			byte[] tp= concat("END".getBytes(), tmp);
			System.arraycopy(tp, 0, dataBuf, 0, tp.length);
			Debug.print("End message with checksum = " + crc.getValue() + " length : " + dataBuf.length);
			Debug.print("Filename length: " + fLen);
			DatagramPacket endpkg = new DatagramPacket(dataBuf, dataBuf.length,
					new InetSocketAddress(nextIp, nextPort));
			System.out.println("Next hop = " + nextIp + ":" + nextPort);
			System.out.println("File sent successfully.");	
			try {
					
					this.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			while (true) {
				try{
				send.setSoTimeout(MyProtocol.FM);
				send.send(endpkg);
				Debug.print("End pkg send out.");
				byte[] secondPart = new byte[dataBuf.length - 11];
				System.arraycopy(dataBuf, 11, secondPart, 0, dataBuf.length - 11);
				byte[] deIP = new byte[12];
				System.arraycopy(dataBuf, 11, deIP, 0, 12);
				CRC32 debug = new CRC32();
				debug.update(secondPart);
				Debug.print("sender end calculated: checksum: " + debug.getValue() + " len: " + dataBuf.length);
				Debug.print(new String(deIP) + "DestIP...");
				send.receive(ackPkg);
				Debug.print(new String(ackPkg.getData()));
				break;
				}catch(SocketTimeoutException e){
					Debug.print("Time out. Resend");
					continue;
				}
			}
			bis.close();
			//send.close();

		} catch (SocketException e) {
			if(send != null){
				send.close();
			}
			if(bis != null){
				bis.close();
			}
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			if(send != null){
				send.close();
			}
			if(bis != null){
				bis.close();
			}
			e.printStackTrace();
		} catch (IOException e) {
			if(send != null){
				send.close();
			}
			if(bis != null){
				bis.close();
			}
			e.printStackTrace();
		}
	}

	public void run() {
		if(this.startPoint)
			try {
				send();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		else
			try {
				pass();
			} catch (Exception e) {
				e.printStackTrace();
			}
	}
	static byte[] concat(byte[] a, byte[] b) {
		byte[] c= new byte[a.length+b.length];
		System.arraycopy(a, 0, c, 0, a.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}
	public byte[] creatHead(){
		byte[] head = new byte[32];
		try {
			byte[] DIP = destIP.getBytes();
			byte[] DP = intBytes(destPort);
			byte[] SIP = InetAddress.getLocalHost().getHostAddress().getBytes();
			byte[] SP = intBytes(DV.selfPort);
			byte[] D = concat(DIP, DP);
			byte[] S = concat(SIP, SP);
			head = concat(D, S);

		} catch (UnknownHostException e) {
			System.out.println("Unvaild destination. ");
			e.printStackTrace();
		}
		return head;
	}
	static byte[] intBytes(int a){
		ByteArrayOutputStream baos=new ByteArrayOutputStream();  
		DataOutputStream dos=new DataOutputStream(baos);  
		try {
			dos.writeInt(a);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return baos.toByteArray(); 

	}
	static byte[] longBytes(long a){
		ByteArrayOutputStream baos=new ByteArrayOutputStream();  
		DataOutputStream dos=new DataOutputStream(baos);  
		try {
			dos.writeLong(a);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return baos.toByteArray(); 
	}
}
//Reference:http://1320438999.iteye.com/blog/1566104
