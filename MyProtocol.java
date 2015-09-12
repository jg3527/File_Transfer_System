
public class MyProtocol {
public static final String LINKUP = "LINKUP";
public static final String LINKDOWN = "LINKDOWN";
public static final String DV = "DV";
public static final String SHOWRT = "SHOWRT";
public static final String CHANGECOST = "CHANGECOST";
public static final String CLOSE = "CLOSE";
public static final String TRANSFER = "TRANSFER";
public static final String ADDPROXY = "ADDPROXY";
public static final String REMOVEPROXY = "REMOVEPROXY";
public static final String END = "END"; //The keyword of the last package of file transfer.
public static final int  MAX = 3996; //The maximum length of file data a UDP package can carry.
public static final int FM = MAX + 48 + 4; //The maximum length of the data of a UDP package.
public static final String file = "D:\\CN2\\client0.txt"; //Initialization file path.
public static final String RVDFileDir = "/home/jg3527/Desktop/";

}
