package client;

/**
 * Interface lắng nghe tiến trình truyền dữ liệu (Upload / Download)
 * Cung cấp số byte đã truyền, tổng kích thước file và tốc độ truyền
 */
public interface TransferProgressListener {
    void onProgress(long transferredBytes, long totalBytes, double speedKbps);
}
