import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class ShowNetworkInfo {
    public static void main(String[] args) throws Exception {
        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface ni : Collections.list(all)) {
            System.out.println("==================================================");
            System.out.println("Name       : " + ni.getName());
            System.out.println("Display    : " + ni.getDisplayName());
            System.out.println("Up         : " + ni.isUp());
            System.out.println("Loopback   : " + ni.isLoopback());
            System.out.println("Virtual    : " + ni.isVirtual());
            System.out.println("Multicast  : " + ni.supportsMulticast());
            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                if (addr instanceof Inet4Address) {
                    System.out.println("IPv4       : " + addr.getHostAddress());
                }
            }
        }
    }
}
