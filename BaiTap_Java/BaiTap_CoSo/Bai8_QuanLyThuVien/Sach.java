public class Sach extends TaiLieu {
    private String tacGia;
    private int soTrang;

    public Sach(String maTL, String tenTL, int namXB, String tacGia, int soTrang) {
        super(maTL, tenTL, namXB);
        this.tacGia = tacGia;
        this.soTrang = soTrang;
    }

    public String getTacGia() {
        return tacGia;
    }

    public void setTacGia(String tacGia) {
        this.tacGia = tacGia;
    }

    public int getSoTrang() {
        return soTrang;
    }

    public void setSoTrang(int soTrang) {
        this.soTrang = soTrang;
    }

    @Override
    public void hienThiThongTin() {
        System.out.printf("[SÁCH] %s | Tác giả: %-18s | Số trang: %d\n", super.toString(), tacGia, soTrang);
    }
}
