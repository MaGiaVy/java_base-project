public class TapChi extends TaiLieu {
    private int soPhatHanh;
    private int thangPhatHanh;

    public TapChi(String maTL, String tenTL, int namXB, int soPhatHanh, int thangPhatHanh) {
        super(maTL, tenTL, namXB);
        this.soPhatHanh = soPhatHanh;
        this.thangPhatHanh = thangPhatHanh;
    }

    public int getSoPhatHanh() {
        return soPhatHanh;
    }

    public void setSoPhatHanh(int soPhatHanh) {
        this.soPhatHanh = soPhatHanh;
    }

    public int getThangPhatHanh() {
        return thangPhatHanh;
    }

    public void setThangPhatHanh(int thangPhatHanh) {
        this.thangPhatHanh = thangPhatHanh;
    }

    @Override
    public void hienThiThongTin() {
        System.out.printf("[TẠP CHÍ] %s | Số phát hành: %-5d | Tháng phát hành: %d\n",
                super.toString(), soPhatHanh, thangPhatHanh);
    }
}
