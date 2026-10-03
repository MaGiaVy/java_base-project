public class SinhVien {
    private String maSV;
    private String hoTen;
    private String lop;
    private double diemTB;

    public SinhVien(String maSV, String hoTen, String lop, double diemTB) {
        this.maSV = maSV;
        this.hoTen = hoTen;
        this.lop = lop;
        this.diemTB = diemTB;
    }

    public String getMaSV() {
        return maSV;
    }

    public void setMaSV(String maSV) {
        this.maSV = maSV;
    }

    public String getHoTen() {
        return hoTen;
    }

    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }

    public String getLop() {
        return lop;
    }

    public void setLop(String lop) {
        this.lop = lop;
    }

    public double getDiemTB() {
        return diemTB;
    }

    public void setDiemTB(double diemTB) {
        this.diemTB = diemTB;
    }

    // Chuyển đối tượng thành định dạng lưu file: MaSV;HoTen;Lop;DiemTB
    public String toFileLine() {
        return String.format("%s;%s;%s;%.2f", maSV, hoTen, lop, diemTB);
    }

    @Override
    public String toString() {
        return String.format("Mã SV: %-10s | Họ tên: %-22s | Lớp: %-10s | Điểm TB: %5.2f",
                maSV, hoTen, lop, diemTB);
    }
}
