package test;

import client.FtpClient;
import common.FtpFileEntry;
import server.FtpServer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FtpIntegrationTest {
    public static void main(String[] args) {
        System.out.println("========== BẮT ĐẦU KIỂM THỬ TÍCH HỢP TOÀN DIỆN (10 TIÊU CHÍ) ==========");

        int testPort = 2199;
        Path testRoot = Paths.get("FtpRoot").toAbsolutePath().normalize();
        Path testLocal = Paths.get("FtpClientLocal").toAbsolutePath().normalize();

        FtpServer server = new FtpServer(testPort, testRoot, msg -> System.out.println("[SERVER LOG] " + msg), () -> {});
        FtpClient client = new FtpClient(msg -> System.out.println("[CLIENT LOG] " + msg));

        try {
            // 1. Khởi động Server
            System.out.println("--> [TEST 1] Khởi động FTP Server tại port " + testPort + "...");
            server.start();
            Thread.sleep(500);

            // 2. Kết nối
            System.out.println("--> [TEST 2] Kết nối TCP Control Socket...");
            boolean connected = client.connect("127.0.0.1", testPort);
            assertCheck(connected, "Kết nối Server thành công (Reply 220)");

            // 3. Test đăng nhập sai
            System.out.println("--> [TEST 3] Thử nghiệm đăng nhập sai mật khẩu...");
            boolean badLogin = client.login("student", "wrong_password");
            assertCheck(!badLogin, "Từ chối đăng nhập sai mật khẩu (Reply 530)");

            // 4. Test đăng nhập đúng
            System.out.println("--> [TEST 4] Đăng nhập đúng với student / 123456...");
            boolean goodLogin = client.login("student", "123456");
            assertCheck(goodLogin, "Đăng nhập thành công (Reply 230)");

            // 5. Test PWD, CWD, CDUP
            System.out.println("--> [TEST 5] Điều hướng PWD, CWD, CDUP...");
            String pwd1 = client.pwd();
            assertCheck(pwd1.equals("/"), "Thư mục ban đầu là / (PWD: " + pwd1 + ")");

            boolean cwdOk = client.cwd("documents");
            assertCheck(cwdOk, "Chuyển thư mục vào /documents thành công (CWD)");

            String pwd2 = client.pwd();
            assertCheck(pwd2.contains("documents"), "Thư mục hiện tại là /documents");

            boolean cdupOk = client.cdup();
            assertCheck(cdupOk, "Lùi về thư mục cha thành công (CDUP)");

            // 6. Test LIST (PASV Mode)
            System.out.println("--> [TEST 6] Lấy danh sách tập tin qua Data Socket (LIST / PASV)...");
            List<FtpFileEntry> entries = client.listFiles("");
            assertCheck(!entries.isEmpty(), "Nhận danh sách file (" + entries.size() + " mục)");
            for (FtpFileEntry fe : entries) {
                System.out.println("    + " + (fe.isDirectory() ? "[DIR]" : "[FILE]") + " " + fe.getName() + " (" + fe.getFormattedSize() + ")");
            }

            // 7. Test RETR (Download)
            System.out.println("--> [TEST 7] Tải file nhị phân report.pdf (RETR / TYPE I)...");
            File destPdf = testLocal.resolve("downloads/test_report.pdf").toFile();
            boolean dlOk = client.downloadFile("documents/report.pdf", destPdf, (trans, total, speed) -> {});
            assertCheck(dlOk && destPdf.exists() && destPdf.length() > 0, "Download file report.pdf thành công (" + destPdf.length() + " bytes)");

            // 8. Test STOR (Upload)
            System.out.println("--> [TEST 8] Tải file lên Server (STOR / TYPE I)...");
            File uploadSample = testLocal.resolve("client_upload_sample.txt").toFile();
            boolean upOk = client.uploadFile(uploadSample, "upload/uploaded_by_test.txt", (trans, total, speed) -> {});
            assertCheck(upOk, "Upload file lên upload/uploaded_by_test.txt thành công");

            File uploadedOnServer = testRoot.resolve("upload/uploaded_by_test.txt").toFile();
            assertCheck(uploadedOnServer.exists(), "Tập tin đã xuất hiện trên Server thực tế");

            // 9. Test Phân quyền RBAC & Quản lý file (DELE, MKD, RMD)
            System.out.println("--> [TEST 9] Kiểm tra phân quyền RBAC (Slide 30): Student tạo thư mục nhưng không được xóa...");
            boolean mkdOk = client.makeDirectory("upload/temp_test_dir");
            assertCheck(mkdOk, "Student tạo thư mục MKD thành công");

            boolean studentDeleFailed = !client.deleteFile("upload/uploaded_by_test.txt");
            assertCheck(studentDeleFailed, "Bảo mật RBAC: Chặn Student xóa file (Reply 550)");

            // Chuyển sang tài khoản ADMIN để thực hiện xóa dọn dẹp
            System.out.println("--> [TEST 9.1] Đăng nhập quyền ADMIN để xóa dọn dẹp (DELE, RMD)...");
            client.disconnect();
            client.connect("127.0.0.1", testPort);
            boolean adminLogin = client.login("admin", "admin");
            assertCheck(adminLogin, "Đăng nhập ADMIN thành công");

            boolean rmdOk = client.removeDirectory("upload/temp_test_dir");
            assertCheck(rmdOk, "Admin xóa thư mục RMD thành công");

            boolean deleOk = client.deleteFile("upload/uploaded_by_test.txt");
            assertCheck(deleOk, "Admin xóa file DELE thành công");

            // 10. Ngắt kết nối & Dừng Server
            System.out.println("--> [TEST 10] Đóng kết nối QUIT...");
            client.disconnect();
            assertCheck(!client.isConnected(), "Client đã ngắt kết nối an toàn (QUIT 221)");

            System.out.println("\n🎉 CHÚC MỪNG: TẤT CẢ 10/10 CA KIỂM THỬ ĐỀU VƯỢT QUA XUẤT SẮC 100%! 🎉");
        } catch (Exception e) {
            System.err.println("❌ LỖI TRONG QUÁ TRÌNH TEST: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("========== KẾT THÚC KIỂM THỬ ==========");
        }
    }

    private static void assertCheck(boolean condition, String message) {
        if (condition) {
            System.out.println("    [PASSED] " + message);
        } else {
            System.err.println("    [FAILED] " + message);
            throw new RuntimeException("Test assertion failed: " + message);
        }
    }
}
