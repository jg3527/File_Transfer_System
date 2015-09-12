import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Debug {
	public static void print(String s){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String currentTime = dateFormat.format(date); 
		//System.out.println(currentTime + " Debug: " + Thread.currentThread().getName() + " " + s);
	}
}
