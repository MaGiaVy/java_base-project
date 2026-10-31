package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Kiểm tra bảo mật đường dẫn và chống triệt để lỗ hổng Path Traversal (Slide 29, 30)
 */
public class SecurityValidator {

    /**
     * Phân giải và kiểm tra an toàn đường dẫn file/thư mục từ yêu cầu của Client
     *
     * @param rootDir Thư mục gốc FtpRoot trên máy chủ
     * @param currentRelativePath Đường dẫn thư mục hiện tại của Session (ví dụ "/" hoặc "/documents")
     * @param targetParam Tham số Client gửi lên (ví dụ "report.pdf", "data", "../", "/images")
     * @return Path vật lý an toàn trên hệ điều hành
     * @throws SecurityException Nếu phát hiện cố tình truy cập ra ngoài FtpRoot
     */
    public static Path resolveSafePath(Path rootDir, String currentRelativePath, String targetParam) throws SecurityException {
        Path rootAbs = rootDir.toAbsolutePath().normalize();

        String cleanTarget = targetParam != null ? targetParam.trim().replace('\\', '/') : "";

        Path resolved;
        if (cleanTarget.startsWith("/")) {
            // Đường dẫn tuyệt đối tính từ FtpRoot
            String rel = cleanTarget.substring(1);
            resolved = rootAbs.resolve(rel).normalize();
        } else {
            // Đường dẫn tương đối tính từ thư mục làm việc hiện tại
            String cur = (currentRelativePath == null || currentRelativePath.equals("/")) ? "" : currentRelativePath;
            if (cur.startsWith("/")) cur = cur.substring(1);
            Path curDir = cur.isEmpty() ? rootAbs : rootAbs.resolve(cur).normalize();
            resolved = curDir.resolve(cleanTarget).normalize();
        }

        // Kiểm tra sống còn: Không được phép vượt khỏi FtpRoot
        if (!resolved.startsWith(rootAbs)) {
            throw new SecurityException("Cảnh báo bảo mật: Cố tình truy cập vượt quyền ngoài FTP Root (Path Traversal Attempt)!");
        }

        return resolved;
    }

    /**
     * Chuyển đổi một Path vật lý trong FtpRoot thành đường dẫn ảo hiển thị cho Client (dạng "/")
     */
    public static String toVirtualPath(Path rootDir, Path physicalPath) {
        Path rootAbs = rootDir.toAbsolutePath().normalize();
        Path targetAbs = physicalPath.toAbsolutePath().normalize();

        if (targetAbs.equals(rootAbs)) {
            return "/";
        }

        Path relative = rootAbs.relativize(targetAbs);
        String vPath = "/" + relative.toString().replace('\\', '/');
        return vPath;
    }
}
