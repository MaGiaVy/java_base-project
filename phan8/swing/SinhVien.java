import java.time.LocalDate;

public class SinhVien {
    private String maSV;
    private String hoTen;
    private String lop;
    private LocalDate ngaySinh;
    private double diemTB;

    // Constructor
    public SinhVien(String maSV, String hoTen, String lop, LocalDate ngaySinh, double diemTB) {
        this.maSV = maSV;
        this.hoTen = hoTen;
        this.lop = lop;
        this.ngaySinh = ngaySinh;
        this.diemTB = diemTB;
    }

    // Getter & Setter
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

    public LocalDate getNgaySinh() {
        return ngaySinh;
    }

    public void setNgaySinh(LocalDate ngaySinh) {
        this.ngaySinh = ngaySinh;
    }

    public double getDiemTB() {
        return diemTB;
    }

    public void setDiemTB(double diemTB) {
        this.diemTB = diemTB;
    }

    // Phương thức kiểm tra điểm hợp lệ
    public static boolean kiemTraDiem(double diem) {
        return diem >= 0 && diem <= 10;
    }

    @Override
    public String toString() {
        return "SinhVien{" +
                "maSV='" + maSV + '\'' +
                ", hoTen='" + hoTen + '\'' +
                ", lop='" + lop + '\'' +
                ", ngaySinh=" + ngaySinh +
                ", diemTB=" + diemTB +
                '}';
    }
}
