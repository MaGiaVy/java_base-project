import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AttachmentItem {
    private final String name;
    private final byte[] data;
    private final String contentType;
    private final File sourceFile;

    public AttachmentItem(String name, byte[] data, String contentType) {
        this.name = name;
        this.data = data;
        this.contentType = contentType != null ? contentType : "application/octet-stream";
        this.sourceFile = null;
    }

    public AttachmentItem(File file) throws IOException {
        this.name = file.getName();
        this.sourceFile = file;
        if (file.isFile()) {
            this.data = Files.readAllBytes(file.toPath());
            String ct = Files.probeContentType(file.toPath());
            this.contentType = ct != null ? ct : "application/octet-stream";
        } else {
            this.data = new byte[0];
            this.contentType = "application/x-directory";
        }
    }

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public String getContentType() {
        return contentType;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public boolean isDirectory() {
        return sourceFile != null && sourceFile.isDirectory();
    }

    public void saveTo(File destination) throws IOException {
        if (data == null) {
            throw new IOException("Không có dữ liệu nhị phân để lưu");
        }
        Path parent = destination.toPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destination.toPath(), data);
    }

    @Override
    public String toString() {
        return name + (data != null && data.length > 0 ? " (" + (data.length / 1024) + " KB)" : "");
    }
}
