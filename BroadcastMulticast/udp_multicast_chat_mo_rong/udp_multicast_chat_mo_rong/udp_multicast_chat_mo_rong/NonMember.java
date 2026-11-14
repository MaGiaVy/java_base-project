import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * May doi chung: van ket noi cung LAN/Wi-Fi nhung KHONG join multicast group.
 * Chuong trinh nay khong mo MulticastSocket, nen khong tham gia nhom.
 */
public class NonMember {
    public static void main(String[] args) {
        String name = args.length >= 1 ? args[0] : "MayKhongThamGia";
        System.out.println("============================================================");
        System.out.println(" MAY DOI CHUNG - KHONG THAM GIA MULTICAST GROUP");
        System.out.println("============================================================");
        System.out.println("Ten may : " + name);
        System.out.println("Thoi gian: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println();
        System.out.println("May nay co the van ket noi cung Wi-Fi/LAN.");
        System.out.println("Nhung chuong trinh KHONG goi joinGroup(), KHONG mo MulticastSocket port 8000.");
        System.out.println("=> No khong phai thanh vien cac nhom 239.1.1.x va se khong hien thi tin chat.");
        System.out.println();
        System.out.println("Nhan Enter de thoat.");
        new Scanner(System.in).nextLine();
    }
}
