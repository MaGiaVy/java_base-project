public abstract class TaiLieu {
    protected String maTL;
    protected String tenTL;
    protected int namXB;

    public TaiLieu(String maTL, String tenTL, int namXB) {
        this.maTL = maTL;
        this.tenTL = tenTL;
        this.namXB = namXB;
    }

    public String getMaTL() {
        return maTL;
    }

    public void setMaTL(String maTL) {
        this.maTL = maTL;
    }

    public String getTenTL() {
        return tenTL;
    }

    public void setTenTL(String tenTL) {
        this.tenTL = tenTL;
    }

    public int getNamXB() {
        return namXB;
    }

    public void setNamXB(int namXB) {
        this.namXB = namXB;
    }

    public abstract void hienThiThongTin();

    @Override
    public String toString() {
        return String.format("Mã: %-8s | Tên: %-25s | Năm XB: %d", maTL, tenTL, namXB);
    }
}
