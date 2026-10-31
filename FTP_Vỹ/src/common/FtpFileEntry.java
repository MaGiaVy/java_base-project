package common;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Đại diện cho một file hoặc thư mục trên FTP Server hoặc Local
 */
public class FtpFileEntry {
    private final String name;
    private final long size;
    private final boolean isDirectory;
    private final String permissions;
    private final long lastModified;

    public FtpFileEntry(String name, long size, boolean isDirectory, String permissions, long lastModified) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.permissions = permissions != null ? permissions : (isDirectory ? "drwxr-xr-x" : "-rw-r--r--");
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getPermissions() {
        return permissions;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getFormattedSize() {
        if (isDirectory) return "<DIR>";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    public String getFormattedDate() {
        if (lastModified <= 0) return "--";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(lastModified));
    }

    /**
     * Định dạng dòng liệt kê danh sách Unix chuẩn theo RFC 959 (phục vụ FileZilla)
     */
    public String toUnixListString() {
        String perm = isDirectory ? "drwxr-xr-x" : "-rw-r--r--";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", java.util.Locale.ENGLISH);
        String dateStr = sdf.format(new Date(lastModified > 0 ? lastModified : System.currentTimeMillis()));
        return String.format("%s   1 ftp      ftp  %10d %s %s", perm, isDirectory ? 0 : size, dateStr, name);
    }
}
