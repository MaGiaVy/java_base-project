public class Sach {
    private String maSach;
    private String tenSach;
    private String tacGia;
    private int soLuong;

    public Sach(String maSach, String tenSach, String tacGia, int soLuong) {
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.soLuong = soLuong;
    }

    public String getMaSach() {
        return maSach;
    }

    public void setMaSach(String maSach) {
        this.maSach = maSach;
    }

    public String getTenSach() {
        return tenSach;
    }

    public void setTenSach(String tenSach) {
        this.tenSach = tenSach;
    }

    public String getTacGia() {
        return tacGia;
    }

    public void setTacGia(String tacGia) {
        this.tacGia = tacGia;
    }

    public int getSoLuong() {
        return soLuong;
    }

    public void setSoLuong(int soLuong) {
        this.soLuong = soLuong;
    }

    @Override
    public String toString() {
        return String.format("Mã: %-8s | Tên sách: %-25s | Tác giả: %-18s | Tồn kho: %d",
                maSach, tenSach, tacGia, soLuong);
    }
}
