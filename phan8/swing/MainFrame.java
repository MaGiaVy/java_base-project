import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class MainFrame extends JFrame {

    // ===== Components =====
    private JTextField txtMaSV, txtHoTen, txtLop, txtNgaySinh, txtDiemTB;
    private JTextField txtSearch;
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblCount, lblAvg, lblMax;

    // ===== DAO =====
    private SinhVienDAO dao;

    // ===== Date format =====
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public MainFrame() {
        dao = new SinhVienDAO();
        initUI();
        loadTable();
        updateStats();
    }

    // ============================================================
    // INIT UI
    // ============================================================
    private void initUI() {
        setTitle("Quản Lý Sinh Viên - Java Swing + PostgreSQL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // Menu Bar
        createMenuBar();

        // Panel trên: Input fields + Buttons
        add(createInputPanel(), BorderLayout.NORTH);

        // Panel giữa: JTable
        add(createTablePanel(), BorderLayout.CENTER);

        // Panel phải: Search + Sort + Stats
        add(createRightPanel(), BorderLayout.EAST);
    }

    // ===== MENU BAR =====
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu File
        JMenu menuFile = new JMenu("File");
        JMenuItem miThoat = new JMenuItem("Thoát");
        miThoat.addActionListener(e -> System.exit(0));
        menuFile.add(miThoat);

        // Menu Chức năng
        JMenu menuChucNang = new JMenu("Chức năng");
        JMenuItem miThem = new JMenuItem("Thêm sinh viên");
        miThem.addActionListener(e -> themSinhVien());
        JMenuItem miSua = new JMenuItem("Sửa sinh viên");
        miSua.addActionListener(e -> suaSinhVien());
        JMenuItem miXoa = new JMenuItem("Xóa sinh viên");
        miXoa.addActionListener(e -> xoaSinhVien());
        JMenuItem miRefresh = new JMenuItem("Làm mới");
        miRefresh.addActionListener(e -> lamMoi());
        menuChucNang.add(miThem);
        menuChucNang.add(miSua);
        menuChucNang.add(miXoa);
        menuChucNang.addSeparator();
        menuChucNang.add(miRefresh);

        // Menu Về
        JMenu menuVe = new JMenu("Về");
        JMenuItem miAbout = new JMenuItem("Thông tin");
        miAbout.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Quản Lý Sinh Viên v1.0\nJava Swing + PostgreSQL\nBài tập Phần 8",
                "Thông tin", JOptionPane.INFORMATION_MESSAGE));
        menuVe.add(miAbout);

        menuBar.add(menuFile);
        menuBar.add(menuChucNang);
        menuBar.add(menuVe);
        setJMenuBar(menuBar);
    }

    // ===== INPUT PANEL (NORTH) =====
    private JPanel createInputPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Thông tin sinh viên"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: MaSV + HoTen
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Mã SV:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtMaSV = new JTextField(12);
        panel.add(txtMaSV, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Họ tên:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1;
        txtHoTen = new JTextField(18);
        panel.add(txtHoTen, gbc);

        // Row 2: Lop + NgaySinh
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Lớp:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtLop = new JTextField(12);
        panel.add(txtLop, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Ngày sinh (yyyy-MM-dd):"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1;
        txtNgaySinh = new JTextField(12);
        panel.add(txtNgaySinh, gbc);

        // Row 3: DiemTB + Buttons
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("Điểm TB:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtDiemTB = new JTextField(8);
        panel.add(txtDiemTB, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnThem = new JButton("Thêm");
        JButton btnSua = new JButton("Sửa");
        JButton btnXoa = new JButton("Xóa");
        JButton btnLamMoi = new JButton("Làm mới");

        btnThem.addActionListener(e -> themSinhVien());
        btnSua.addActionListener(e -> suaSinhVien());
        btnXoa.addActionListener(e -> xoaSinhVien());
        btnLamMoi.addActionListener(e -> lamMoi());

        btnPanel.add(btnThem);
        btnPanel.add(btnSua);
        btnPanel.add(btnXoa);
        btnPanel.add(btnLamMoi);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        panel.add(btnPanel, gbc);
        gbc.gridwidth = 1; // reset

        return panel;
    }

    // ===== TABLE PANEL (CENTER) =====
    private JScrollPane createTablePanel() {
        String[] columns = { "Mã SV", "Họ Tên", "Lớp", "Ngày Sinh", "Điểm TB" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Không cho sửa trực tiếp trên bảng
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(25);
        table.getTableHeader().setReorderingAllowed(false);

        // Click row → Hiển thị lên TextBox
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    txtMaSV.setText(tableModel.getValueAt(row, 0).toString());
                    txtHoTen.setText(tableModel.getValueAt(row, 1).toString());
                    txtLop.setText(tableModel.getValueAt(row, 2).toString());
                    Object ngaySinh = tableModel.getValueAt(row, 3);
                    txtNgaySinh.setText(ngaySinh != null ? ngaySinh.toString() : "");
                    txtDiemTB.setText(tableModel.getValueAt(row, 4).toString());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách sinh viên"));
        return scrollPane;
    }

    // ===== RIGHT PANEL (EAST): Search + Sort + Stats =====
    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(220, 0));

        // --- Tìm kiếm ---
        JPanel searchPanel = new JPanel();
        searchPanel.setBorder(BorderFactory.createTitledBorder("Tìm kiếm"));
        searchPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        searchPanel.setMaximumSize(new Dimension(220, 100));

        txtSearch = new JTextField(14);
        JButton btnTim = new JButton("Tìm");
        JButton btnReset = new JButton("Reset");

        btnTim.addActionListener(e -> timKiem());
        btnReset.addActionListener(e -> {
            txtSearch.setText("");
            loadTable();
            updateStats();
        });

        searchPanel.add(txtSearch);
        searchPanel.add(btnTim);
        searchPanel.add(btnReset);

        // --- Sắp xếp ---
        JPanel sortPanel = new JPanel();
        sortPanel.setBorder(BorderFactory.createTitledBorder("Sắp xếp"));
        sortPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        sortPanel.setMaximumSize(new Dimension(220, 70));

        JButton btnSortTen = new JButton("Theo Tên");
        JButton btnSortDiem = new JButton("Theo Điểm");

        btnSortTen.addActionListener(e -> {
            List<SinhVien> list = dao.sortByName();
            updateTable(list);
        });
        btnSortDiem.addActionListener(e -> {
            List<SinhVien> list = dao.sortByDiem();
            updateTable(list);
        });

        sortPanel.add(btnSortTen);
        sortPanel.add(btnSortDiem);

        // --- Thống kê ---
        JPanel statsPanel = new JPanel();
        statsPanel.setBorder(BorderFactory.createTitledBorder("Thống kê"));
        statsPanel.setLayout(new GridLayout(3, 1, 5, 5));
        statsPanel.setMaximumSize(new Dimension(220, 120));

        lblCount = new JLabel("Số lượng SV: 0");
        lblAvg = new JLabel("Điểm TB chung: 0.00");
        lblMax = new JLabel("SV điểm cao nhất: -");

        lblCount.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblAvg.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblMax.setFont(new Font("SansSerif", Font.BOLD, 12));

        statsPanel.add(lblCount);
        statsPanel.add(lblAvg);
        statsPanel.add(lblMax);

        panel.add(Box.createVerticalStrut(5));
        panel.add(searchPanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(sortPanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(statsPanel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    // ============================================================
    // CHỨC NĂNG CRUD
    // ============================================================

    // --- Load tất cả sinh viên lên bảng ---
    private void loadTable() {
        List<SinhVien> list = dao.getAllSinhVien();
        updateTable(list);
    }

    // --- Cập nhật bảng từ danh sách ---
    private void updateTable(List<SinhVien> list) {
        tableModel.setRowCount(0); // Xóa dữ liệu cũ
        for (SinhVien sv : list) {
            tableModel.addRow(new Object[] {
                    sv.getMaSV(),
                    sv.getHoTen(),
                    sv.getLop(),
                    sv.getNgaySinh() != null ? sv.getNgaySinh().format(DATE_FMT) : "",
                    sv.getDiemTB()
            });
        }
    }

    // --- Cập nhật thống kê ---
    private void updateStats() {
        try {
            int count = dao.getCountSinhVien();
            double avg = dao.getAvgDiem();
            SinhVien top = dao.getTopStudent();

            lblCount.setText("Số lượng SV: " + count);
            lblAvg.setText(String.format("Điểm TB chung: %.2f", avg));
            if (top != null) {
                lblMax.setText("Max: " + top.getHoTen() + " (" + top.getDiemTB() + ")");
            } else {
                lblMax.setText("SV điểm cao nhất: -");
            }
        } catch (Exception e) {
            System.err.println("Lỗi updateStats: " + e.getMessage());
        }
    }

    // --- THÊM sinh viên ---
    private void themSinhVien() {
        try {
            String maSV = txtMaSV.getText().trim();
            String hoTen = txtHoTen.getText().trim();
            String lop = txtLop.getText().trim();
            String ngaySinhStr = txtNgaySinh.getText().trim();
            String diemStr = txtDiemTB.getText().trim();

            // Validate: không để trống
            if (maSV.isEmpty() || hoTen.isEmpty() || lop.isEmpty() || diemStr.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập đầy đủ thông tin (Mã SV, Họ tên, Lớp, Điểm TB)!",
                        "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate: điểm 0-10
            double diemTB = Double.parseDouble(diemStr);
            if (!SinhVien.kiemTraDiem(diemTB)) {
                JOptionPane.showMessageDialog(this,
                        "Điểm trung bình phải từ 0 đến 10!",
                        "Lỗi điểm", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Parse ngày sinh (có thể bỏ trống)
            LocalDate ngaySinh = null;
            if (!ngaySinhStr.isEmpty()) {
                try {
                    ngaySinh = LocalDate.parse(ngaySinhStr, DATE_FMT);
                } catch (DateTimeParseException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Ngày sinh không hợp lệ! Định dạng: yyyy-MM-dd (ví dụ: 2003-01-15)",
                            "Lỗi ngày sinh", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            SinhVien sv = new SinhVien(maSV, hoTen, lop, ngaySinh, diemTB);
            boolean ok = dao.addSinhVien(sv);

            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Thêm sinh viên thành công!", "Thành công",
                        JOptionPane.INFORMATION_MESSAGE);
                loadTable();
                updateStats();
                clearInputs();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Thêm thất bại! Mã SV có thể đã tồn tại.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Điểm TB phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- SỬA sinh viên ---
    private void suaSinhVien() {
        try {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng chọn sinh viên cần sửa trên bảng!",
                        "Chưa chọn", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String maSV = txtMaSV.getText().trim();
            String hoTen = txtHoTen.getText().trim();
            String lop = txtLop.getText().trim();
            String ngaySinhStr = txtNgaySinh.getText().trim();
            String diemStr = txtDiemTB.getText().trim();

            // Validate: không để trống
            if (maSV.isEmpty() || hoTen.isEmpty() || lop.isEmpty() || diemStr.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng nhập đầy đủ thông tin!",
                        "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate: điểm 0-10
            double diemTB = Double.parseDouble(diemStr);
            if (!SinhVien.kiemTraDiem(diemTB)) {
                JOptionPane.showMessageDialog(this,
                        "Điểm trung bình phải từ 0 đến 10!",
                        "Lỗi điểm", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Parse ngày sinh
            LocalDate ngaySinh = null;
            if (!ngaySinhStr.isEmpty()) {
                try {
                    ngaySinh = LocalDate.parse(ngaySinhStr, DATE_FMT);
                } catch (DateTimeParseException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Ngày sinh không hợp lệ! Định dạng: yyyy-MM-dd",
                            "Lỗi ngày sinh", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            SinhVien sv = new SinhVien(maSV, hoTen, lop, ngaySinh, diemTB);
            boolean ok = dao.updateSinhVien(sv);

            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Sửa sinh viên thành công!", "Thành công",
                        JOptionPane.INFORMATION_MESSAGE);
                loadTable();
                updateStats();
                clearInputs();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Sửa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Điểm TB phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- XÓA sinh viên ---
    private void xoaSinhVien() {
        try {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this,
                        "Vui lòng chọn sinh viên cần xóa trên bảng!",
                        "Chưa chọn", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String maSV = tableModel.getValueAt(row, 0).toString();
            String hoTen = tableModel.getValueAt(row, 1).toString();

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn xóa sinh viên: " + hoTen + " (" + maSV + ")?",
                    "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                boolean ok = dao.deleteSinhVien(maSV);
                if (ok) {
                    JOptionPane.showMessageDialog(this,
                            "Xóa thành công!", "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                    loadTable();
                    updateStats();
                    clearInputs();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Xóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- TÌM KIẾM ---
    private void timKiem() {
        String keyword = txtSearch.getText().trim();
        if (keyword.isEmpty()) {
            loadTable();
            return;
        }
        List<SinhVien> list = dao.searchSinhVien(keyword);
        updateTable(list);

        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy sinh viên nào!", "Kết quả",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --- LÀM MỚI ---
    private void lamMoi() {
        clearInputs();
        txtSearch.setText("");
        loadTable();
        updateStats();
    }

    // --- Clear tất cả input fields ---
    private void clearInputs() {
        txtMaSV.setText("");
        txtHoTen.setText("");
        txtLop.setText("");
        txtNgaySinh.setText("");
        txtDiemTB.setText("");
        table.clearSelection();
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        // Chạy trên Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Dùng giao diện hệ thống (Windows look)
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Dùng look and feel mặc định nếu lỗi
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
