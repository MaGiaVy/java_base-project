public class SinhVien {
    private String maSV;
    private String hoTen;
    private String lop;
    private double diemTB;
    
    // Constructor
    public SinhVien(String maSV, String hoTen, String lop, double diemTB) {
        this.maSV = maSV;
        this.hoTen = hoTen;
        this.lop = lop;
        this.diemTB = diemTB;
    }
    
    // Getter methods
    public String getMaSV() {
        return maSV;
    }
    
    public String getHoTen() {
        return hoTen;
    }
    
    public String getLop() {
        return lop;
    }
    
    public double getDiemTB() {
        return diemTB;
    }
    
    // Setter methods
    public void setMaSV(String maSV) {
        this.maSV = maSV;
    }
    
    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }
    
    public void setLop(String lop) {
        this.lop = lop;
    }
    
    public void setDiemTB(double diemTB) {
        this.diemTB = diemTB;
    }
    
    // Phương thức toString để in thông tin
    @Override
    public String toString() {
        return String.format("%-10s | %-25s | %-10s | %-8.2f",
                maSV, hoTen, lop, diemTB);
    }
    
    // Phương thức kiểm tra điểm hợp lệ
    public static boolean kiemTraDiem(double diem) {
        return diem >= 0 && diem <= 10;
    }
}