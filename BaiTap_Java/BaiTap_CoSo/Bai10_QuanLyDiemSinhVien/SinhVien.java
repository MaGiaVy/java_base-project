public class SinhVien {
    private String hoTen;
    private double diemToan;
    private double diemLapTrinh;

    public SinhVien(String hoTen, double diemToan, double diemLapTrinh) {
        this.hoTen = hoTen;
        this.diemToan = diemToan;
        this.diemLapTrinh = diemLapTrinh;
    }

    public String getHoTen() {
        return hoTen;
    }

    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }

    public double getDiemToan() {
        return diemToan;
    }

    public void setDiemToan(double diemToan) {
        this.diemToan = diemToan;
    }

    public double getDiemLapTrinh() {
        return diemLapTrinh;
    }

    public void setDiemLapTrinh(double diemLapTrinh) {
        this.diemLapTrinh = diemLapTrinh;
    }

    // Method tính điểm trung bình
    public double tinhDiemTB() {
        return (diemToan + diemLapTrinh) / 2.0;
    }

    @Override
    public String toString() {
        return String.format("Họ tên: %-20s | Toán: %5.2f | Lập trình: %5.2f | ĐTB: %5.2f",
                hoTen, diemToan, diemLapTrinh, tinhDiemTB());
    }
}
