import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

/**
 * MainFrame.java - Giao diện chính Quản Lý Sinh Viên
 * Modern UI với 4 tabs, Material Design, JFreeChart.
 *
 * Tab 1: Danh Sách (JTable + toolbar)
 * Tab 2: Thêm/Sửa (Form nhập liệu)
 * Tab 3: Tìm Kiếm (Search + checkboxes + kết quả)
 * Tab 4: Thống Kê (Summary + Charts)
 */
public class MainFrame extends JFrame {

    // ===== DAO =====
    private SinhVienDAO dao;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ===== Main Components =====
    private JTabbedPane tabbedPane;

    // ===== Tab 1: Danh Sách =====
    private JTable tableList;
    private DefaultTableModel tableModelList;
    private JLabel lblStatusBar;

    // ===== Tab 2: Thêm/Sửa =====
    private JTextField txtMaSV, txtHoTen, txtLop, txtNgaySinh, txtDiemTB;
    private JButton btnSave;
    private JLabel lblFormTitle;
    private boolean isEditMode = false;

    // ===== Tab 3: Tìm Kiếm =====
    private JTextField txtSearch;
    private JTable tableSearch;
    private DefaultTableModel tableModelSearch;
    private JCheckBox chkMaSV, chkHoTen, chkLop;
    private JLabel lblSearchResult;

    // ===== Tab 4: Thống Kê =====
    private JLabel lblTotalStudents, lblAvgScore, lblTopStudent, lblTop5;
    private JPanel panelChart1, panelChart2;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public MainFrame() {
        dao = new SinhVienDAO();
        initUI();
        loadListTable();
        updateStatusBar();
    }

    // ============================================================
    // INIT UI
    // ============================================================
    private void initUI() {
        setTitle("Quản Lý Sinh Viên - Java Swing + PostgreSQL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Constants.BACKGROUND);
        setLayout(new BorderLayout(0, 0));

        // Menu Bar
        createMenuBar();

        // Title Panel (thanh tiêu đề xanh trên cùng)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Constants.MARGIN, 8));
        titlePanel.setBackground(Constants.PRIMARY);
        JLabel lblTitle = new JLabel("QUẢN LÝ SINH VIÊN - HỆ THỐNG QUẢN LÝ");
        lblTitle.setFont(Constants.FONT_TITLE);
        lblTitle.setForeground(Color.WHITE);
        titlePanel.add(lblTitle);
        add(titlePanel, BorderLayout.NORTH);

        // Tabbed Pane (4 tabs)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Constants.FONT_HEADER);
        tabbedPane.setBackground(Constants.BACKGROUND);
        tabbedPane.addTab("Danh Sách", initTab1_List());
        tabbedPane.addTab(" Thêm/Sửa", initTab2_AddEdit());
        tabbedPane.addTab("Tìm Kiếm", initTab3_Search());
        tabbedPane.addTab("Thống Kê", initTab4_Statistics());

        // Khi chuyển tab → refresh dữ liệu
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx == 0) {
                loadListTable();
                updateStatusBar();
            } else if (idx == 3) {
                updateStatistics();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ============================================================
    // MENU BAR
    // ============================================================
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(Color.WHITE);

        // Menu File
        JMenu menuFile = new JMenu("File");
        JMenuItem miThoat = new JMenuItem("Thoát");
        miThoat.addActionListener(e -> System.exit(0));
        menuFile.add(miThoat);

        // Menu Help
        JMenu menuHelp = new JMenu("Help");
        JMenuItem miAbout = new JMenuItem("Thông tin");
        miAbout.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Quản Lý Sinh Viên v2.0\nJava Swing + PostgreSQL + JFreeChart\nBài tập Phần 8",
                "Thông tin", JOptionPane.INFORMATION_MESSAGE));
        menuHelp.add(miAbout);

        menuBar.add(menuFile);
        menuBar.add(menuHelp);
        setJMenuBar(menuBar);
    }

    // ============================================================
    // TAB 1: DANH SÁCH SINH VIÊN
    // ============================================================
    private JPanel initTab1_List() {
        JPanel panel = new JPanel(new BorderLayout(0, Constants.GAP));
        panel.setBackground(Constants.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(
                Constants.PADDING, Constants.MARGIN, Constants.PADDING, Constants.MARGIN));

        // --- Toolbar ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, Constants.GAP, 0));
        toolbar.setOpaque(false);

        JButton btnRefresh = UIHelper.createStyledButton("Làm mới", Constants.PRIMARY);
        JButton btnEdit = UIHelper.createStyledButton("Sửa", Constants.SUCCESS);
        JButton btnDelete = UIHelper.createStyledButton("Xóa", Constants.DANGER);

        btnRefresh.addActionListener(e -> {
            loadListTable();
            updateStatusBar();
        });
        btnEdit.addActionListener(e -> editFromList());
        btnDelete.addActionListener(e -> deleteSinhVien());

        toolbar.add(btnRefresh);
        toolbar.add(btnEdit);
        toolbar.add(btnDelete);
        panel.add(toolbar, BorderLayout.NORTH);

        // --- JTable ---
        String[] columns = { "Mã SV", "Họ Tên", "Lớp", "Ngày Sinh", "Điểm TB" };
        tableModelList = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableList = new JTable(tableModelList);
        UIHelper.styleTable(tableList);

        // Double-click row → chuyển sang Tab 2 để sửa
        tableList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editFromList();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tableList);
        scrollPane.setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);

        // --- Status Bar ---
        lblStatusBar = new JLabel("Tổng: 0 sinh viên");
        lblStatusBar.setFont(Constants.FONT_BODY);
        lblStatusBar.setForeground(Constants.TEXT_DARK);
        lblStatusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Constants.BORDER),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        panel.add(lblStatusBar, BorderLayout.SOUTH);

        return panel;
    }

    // ============================================================
    // TAB 2: THÊM / SỬA SINH VIÊN
    // ============================================================
    private JPanel initTab2_AddEdit() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Constants.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(
                Constants.MARGIN, Constants.MARGIN * 3, Constants.MARGIN, Constants.MARGIN * 3));

        // --- Form Panel ---
        JPanel formPanel = UIHelper.createTitledPanel("Thông Tin Sinh Viên");
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(Constants.GAP, Constants.GAP, Constants.GAP, Constants.GAP);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Form Title
        lblFormTitle = new JLabel(" THÊM SINH VIÊN MỚI");
        lblFormTitle.setFont(Constants.FONT_HEADER);
        lblFormTitle.setForeground(Constants.SUCCESS);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        formPanel.add(lblFormTitle, gbc);
        gbc.gridwidth = 1;

        // Mã SV
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Mã SV: *"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtMaSV = UIHelper.createTextField(20);
        formPanel.add(txtMaSV, gbc);

        // Họ tên
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Họ tên: *"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtHoTen = UIHelper.createTextField(20);
        formPanel.add(txtHoTen, gbc);

        // Lớp
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Lớp: *"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtLop = UIHelper.createTextField(20);
        formPanel.add(txtLop, gbc);

        // Ngày sinh
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Ngày sinh: * (yyyy-MM-dd)"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtNgaySinh = UIHelper.createTextField(20);
        formPanel.add(txtNgaySinh, gbc);

        // Điểm TB
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Điểm TB: * (0-10)"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtDiemTB = UIHelper.createTextField(20);
        formPanel.add(txtDiemTB, gbc);

        // --- Buttons ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, Constants.GAP, 0));
        btnPanel.setOpaque(false);

        btnSave = UIHelper.createStyledButton("Lưu", Constants.SUCCESS);
        JButton btnClear = UIHelper.createStyledButton("Làm mới", Constants.PRIMARY);
        JButton btnCancel = UIHelper.createStyledButton("Hủy", Constants.DANGER);

        btnSave.addActionListener(e -> saveSinhVien());
        btnClear.addActionListener(e -> {
            clearForm();
            setAddMode();
        });
        btnCancel.addActionListener(e -> {
            clearForm();
            setAddMode();
            tabbedPane.setSelectedIndex(0); // Quay lại Tab 1
        });

        btnPanel.add(btnSave);
        btnPanel.add(btnClear);
        btnPanel.add(btnCancel);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(Constants.MARGIN, Constants.GAP, Constants.GAP, Constants.GAP);
        formPanel.add(btnPanel, gbc);

        panel.add(formPanel, BorderLayout.CENTER);
        return panel;
    }

    // ============================================================
    // TAB 3: TÌM KIẾM
    // ============================================================
    private JPanel initTab3_Search() {
        JPanel panel = new JPanel(new BorderLayout(0, Constants.GAP));
        panel.setBackground(Constants.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(
                Constants.PADDING, Constants.MARGIN, Constants.PADDING, Constants.MARGIN));

        // --- Search Bar ---
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, Constants.GAP, 5));
        searchBar.setOpaque(false);
        searchBar.add(UIHelper.createLabel("Từ khóa:"));
        txtSearch = UIHelper.createTextField(20);
        searchBar.add(txtSearch);
        JButton btnSearch = UIHelper.createStyledButton("Tìm", Constants.PRIMARY);
        btnSearch.addActionListener(e -> performSearch());
        txtSearch.addActionListener(e -> performSearch()); // Enter key
        searchBar.add(btnSearch);

        // --- Checkboxes Filter ---
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Constants.GAP, 0));
        filterPanel.setOpaque(false);
        filterPanel.add(UIHelper.createLabel("Tìm theo:"));
        chkMaSV = new JCheckBox("Mã SV", true);
        chkHoTen = new JCheckBox("Họ tên", true);
        chkLop = new JCheckBox("Lớp", true);
        chkMaSV.setFont(Constants.FONT_BODY);
        chkMaSV.setOpaque(false);
        chkHoTen.setFont(Constants.FONT_BODY);
        chkHoTen.setOpaque(false);
        chkLop.setFont(Constants.FONT_BODY);
        chkLop.setOpaque(false);
        filterPanel.add(chkMaSV);
        filterPanel.add(chkHoTen);
        filterPanel.add(chkLop);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        topPanel.add(searchBar);
        topPanel.add(filterPanel);
        panel.add(topPanel, BorderLayout.NORTH);

        // --- Results Table ---
        String[] columns = { "Mã SV", "Họ Tên", "Lớp", "Ngày Sinh", "Điểm TB" };
        tableModelSearch = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableSearch = new JTable(tableModelSearch);
        UIHelper.styleTable(tableSearch);
        JScrollPane scrollPane = new JScrollPane(tableSearch);
        panel.add(scrollPane, BorderLayout.CENTER);

        // --- Bottom bar: Kết quả + Nút sắp xếp ---
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        lblSearchResult = new JLabel("0 kết quả tìm được");
        lblSearchResult.setFont(Constants.FONT_BODY);
        bottomBar.add(lblSearchResult, BorderLayout.WEST);

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, Constants.GAP, 0));
        sortPanel.setOpaque(false);
        JButton btnSortAZ = UIHelper.createStyledButton("A-Z", Constants.PRIMARY);
        JButton btnSortDiem = UIHelper.createStyledButton("Theo Điểm", Constants.ACCENT);
        JButton btnReset = UIHelper.createStyledButton("Reset", Constants.GRAY_600);

        btnSortAZ.addActionListener(e -> {
            List<SinhVien> list = dao.sortByName();
            updateSearchTable(list);
            lblSearchResult.setText(list.size() + " kết quả (sắp xếp A-Z)");
        });
        btnSortDiem.addActionListener(e -> {
            List<SinhVien> list = dao.sortByDiem();
            updateSearchTable(list);
            lblSearchResult.setText(list.size() + " kết quả (sắp xếp theo điểm)");
        });
        btnReset.addActionListener(e -> {
            txtSearch.setText("");
            tableModelSearch.setRowCount(0);
            lblSearchResult.setText("0 kết quả tìm được");
        });

        sortPanel.add(btnSortAZ);
        sortPanel.add(btnSortDiem);
        sortPanel.add(btnReset);
        bottomBar.add(sortPanel, BorderLayout.EAST);
        panel.add(bottomBar, BorderLayout.SOUTH);

        return panel;
    }

    // ============================================================
    // TAB 4: THỐNG KÊ + BIỂU ĐỒ
    // ============================================================
    private JPanel initTab4_Statistics() {
        JPanel panel = new JPanel(new BorderLayout(Constants.GAP, 0));
        panel.setBackground(Constants.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(
                Constants.PADDING, Constants.MARGIN, Constants.PADDING, Constants.MARGIN));

        // --- Left Panel: Tóm tắt ---
        JPanel leftPanel = UIHelper.createTitledPanel("Tóm tắt");
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(250, 0));

        lblTotalStudents = new JLabel("Tổng SV: 0");
        lblTotalStudents.setFont(Constants.FONT_STATS_LABEL);
        lblTotalStudents.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblAvgScore = new JLabel("Điểm TB: 0.00");
        lblAvgScore.setFont(Constants.FONT_STATS_LABEL);
        lblAvgScore.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTopStudent = new JLabel("Cao nhất: -");
        lblTopStudent.setFont(Constants.FONT_STATS_LABEL);
        lblTopStudent.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTop5 = new JLabel("<html>Top 5 SV:<br>...</html>");
        lblTop5.setFont(Constants.FONT_BODY);
        lblTop5.setAlignmentX(Component.LEFT_ALIGNMENT);

        leftPanel.add(Box.createVerticalStrut(Constants.PADDING));
        leftPanel.add(lblTotalStudents);
        leftPanel.add(Box.createVerticalStrut(Constants.MARGIN));
        leftPanel.add(lblAvgScore);
        leftPanel.add(Box.createVerticalStrut(Constants.MARGIN));
        leftPanel.add(lblTopStudent);
        leftPanel.add(Box.createVerticalStrut(Constants.MARGIN));
        leftPanel.add(lblTop5);
        leftPanel.add(Box.createVerticalGlue());

        // Nút Cập nhật
        JButton btnRefreshStats = UIHelper.createStyledButton("Cập nhật", Constants.PRIMARY);
        btnRefreshStats.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRefreshStats.addActionListener(e -> updateStatistics());
        leftPanel.add(btnRefreshStats);
        leftPanel.add(Box.createVerticalStrut(Constants.PADDING));

        panel.add(leftPanel, BorderLayout.WEST);

        // --- Right Panel: Biểu đồ ---
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, Constants.GAP));
        rightPanel.setOpaque(false);

        panelChart1 = new JPanel(new BorderLayout());
        panelChart1.setBackground(Color.WHITE);
        panelChart1.setBorder(BorderFactory.createTitledBorder("Phân bố Điểm"));

        panelChart2 = new JPanel(new BorderLayout());
        panelChart2.setBackground(Color.WHITE);
        panelChart2.setBorder(BorderFactory.createTitledBorder("Phân bố Lớp"));

        rightPanel.add(panelChart1);
        rightPanel.add(panelChart2);
        panel.add(rightPanel, BorderLayout.CENTER);

        return panel;
    }

    // ============================================================
    // DATA METHODS
    // ============================================================

    // Load tất cả sinh viên lên Tab 1
    private void loadListTable() {
        List<SinhVien> list = dao.getAllSinhVien();
        updateListTable(list);
    }

    // Cập nhật bảng Tab 1
    private void updateListTable(List<SinhVien> list) {
        tableModelList.setRowCount(0);
        for (SinhVien sv : list) {
            tableModelList.addRow(new Object[] {
                    sv.getMaSV(),
                    sv.getHoTen(),
                    sv.getLop(),
                    sv.getNgaySinh() != null ? sv.getNgaySinh().format(DATE_FMT) : "",
                    sv.getDiemTB()
            });
        }
    }

    // Cập nhật bảng Tab 3 (kết quả tìm kiếm)
    private void updateSearchTable(List<SinhVien> list) {
        tableModelSearch.setRowCount(0);
        for (SinhVien sv : list) {
            tableModelSearch.addRow(new Object[] {
                    sv.getMaSV(),
                    sv.getHoTen(),
                    sv.getLop(),
                    sv.getNgaySinh() != null ? sv.getNgaySinh().format(DATE_FMT) : "",
                    sv.getDiemTB()
            });
        }
    }

    // Cập nhật status bar Tab 1
    private void updateStatusBar() {
        int count = dao.getCountSinhVien();
        lblStatusBar.setText("Tổng: " + count + " sinh viên");
    }

    // Refresh tất cả sau CRUD
    private void refreshAll() {
        loadListTable();
        updateStatusBar();
    }

    // ============================================================
    // CRUD METHODS
    // ============================================================

    // Lưu (xử lý cả Thêm và Sửa)
    private void saveSinhVien() {
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

            // Parse ngày sinh
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
            boolean ok;

            if (isEditMode) {
                // === SỬA ===
                ok = dao.updateSinhVien(sv);
                if (ok) {
                    JOptionPane.showMessageDialog(this,
                            "Sửa sinh viên thành công!", "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Sửa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                // === THÊM ===
                ok = dao.addSinhVien(sv);
                if (ok) {
                    JOptionPane.showMessageDialog(this,
                            "Thêm sinh viên thành công!", "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Thêm thất bại! Mã SV có thể đã tồn tại.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            clearForm();
            setAddMode();
            refreshAll();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Điểm TB phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Click row Tab 1 → chuyển sang Tab 2 để sửa
    private void editFromList() {
        int row = tableList.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn sinh viên cần sửa trên bảng!",
                    "Chưa chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Chuyển đổi row index (nếu bảng đang sorted)
        int modelRow = tableList.convertRowIndexToModel(row);

        // Điền form
        txtMaSV.setText(tableModelList.getValueAt(modelRow, 0).toString());
        txtHoTen.setText(tableModelList.getValueAt(modelRow, 1).toString());
        txtLop.setText(tableModelList.getValueAt(modelRow, 2).toString());
        Object ngaySinh = tableModelList.getValueAt(modelRow, 3);
        txtNgaySinh.setText(ngaySinh != null ? ngaySinh.toString() : "");
        txtDiemTB.setText(tableModelList.getValueAt(modelRow, 4).toString());

        setEditMode();
        tabbedPane.setSelectedIndex(1); // Chuyển sang Tab 2
    }

    // Xóa sinh viên (Tab 1)
    private void deleteSinhVien() {
        int row = tableList.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn sinh viên cần xóa trên bảng!",
                    "Chưa chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableList.convertRowIndexToModel(row);
        String maSV = tableModelList.getValueAt(modelRow, 0).toString();
        String hoTen = tableModelList.getValueAt(modelRow, 1).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa sinh viên: " + hoTen + " (" + maSV + ")?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean ok = dao.deleteSinhVien(maSV);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Xóa thành công!", "Thành công",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshAll();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Xóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Tìm kiếm (Tab 3) - với checkboxes filter
    private void performSearch() {
        String keyword = txtSearch.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập từ khóa tìm kiếm!",
                    "Thiếu từ khóa", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Lấy tất cả SV rồi lọc theo checkbox
        List<SinhVien> allStudents = dao.getAllSinhVien();
        List<SinhVien> results = new ArrayList<>();
        String kw = keyword.toLowerCase();

        for (SinhVien sv : allStudents) {
            boolean match = false;
            if (chkMaSV.isSelected() && sv.getMaSV().toLowerCase().contains(kw))
                match = true;
            if (chkHoTen.isSelected() && sv.getHoTen().toLowerCase().contains(kw))
                match = true;
            if (chkLop.isSelected() && sv.getLop().toLowerCase().contains(kw))
                match = true;
            if (match)
                results.add(sv);
        }

        updateSearchTable(results);
        lblSearchResult.setText(results.size() + " kết quả tìm được");

        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy sinh viên nào!", "Kết quả",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ============================================================
    // FORM HELPERS
    // ============================================================

    // Xóa trắng tất cả input fields
    private void clearForm() {
        txtMaSV.setText("");
        txtHoTen.setText("");
        txtLop.setText("");
        txtNgaySinh.setText("");
        txtDiemTB.setText("");
    }

    // Chuyển về chế độ THÊM
    private void setAddMode() {
        isEditMode = false;
        txtMaSV.setEditable(true);
        lblFormTitle.setText("THÊM SINH VIÊN MỚI");
        lblFormTitle.setForeground(Constants.SUCCESS);
        btnSave.setText("Lưu");
    }

    // Chuyển sang chế độ SỬA
    private void setEditMode() {
        isEditMode = true;
        txtMaSV.setEditable(false); // Không cho đổi Mã SV khi sửa
        lblFormTitle.setText("SỬA THÔNG TIN SINH VIÊN");
        lblFormTitle.setForeground(Constants.PRIMARY);
        btnSave.setText("Cập nhật");
    }

    // ============================================================
    // TAB 4: CẬP NHẬT THỐNG KÊ + BIỂU ĐỒ
    // ============================================================

    private void updateStatistics() {
        try {
            // Tóm tắt số liệu
            int count = dao.getCountSinhVien();
            double avg = dao.getAvgDiem();
            SinhVien top = dao.getTopStudent();

            lblTotalStudents.setText("Tổng SV: " + count);
            lblAvgScore.setText(String.format("Điểm TB: %.2f / 10", avg));

            if (top != null) {
                lblTopStudent.setText("Cao nhất: " + top.getHoTen()
                        + " (" + top.getDiemTB() + ")");
            } else {
                lblTopStudent.setText("Cao nhất: -");
            }

            // Top 5 sinh viên điểm cao nhất
            List<SinhVien> sortedByDiem = dao.sortByDiem();
            StringBuilder top5Text = new StringBuilder("<html>Top 5 SV:<br>");
            int limit = Math.min(5, sortedByDiem.size());
            for (int i = 0; i < limit; i++) {
                SinhVien sv = sortedByDiem.get(i);
                top5Text.append("&nbsp;&nbsp;").append(i + 1).append(". ")
                        .append(sv.getHoTen()).append(" (").append(sv.getDiemTB()).append(")<br>");
            }
            top5Text.append("</html>");
            lblTop5.setText(top5Text.toString());

            // Vẽ biểu đồ
            createScoreHistogram();
            createClassDistribution();

        } catch (Exception e) {
            System.err.println("Lỗi updateStatistics: " + e.getMessage());
        }
    }

    // Biểu đồ cột: Phân bố điểm (0-2, 2-4, 4-6, 6-8, 8-10)
    private void createScoreHistogram() {
        try {
            List<SinhVien> allStudents = dao.getAllSinhVien();
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            int[] ranges = new int[5];
            for (SinhVien sv : allStudents) {
                double d = sv.getDiemTB();
                if (d < 2)
                    ranges[0]++;
                else if (d < 4)
                    ranges[1]++;
                else if (d < 6)
                    ranges[2]++;
                else if (d < 8)
                    ranges[3]++;
                else
                    ranges[4]++;
            }

            dataset.addValue(ranges[0], "Sinh viên", "0-2");
            dataset.addValue(ranges[1], "Sinh viên", "2-4");
            dataset.addValue(ranges[2], "Sinh viên", "4-6");
            dataset.addValue(ranges[3], "Sinh viên", "6-8");
            dataset.addValue(ranges[4], "Sinh viên", "8-10");

            JFreeChart chart = ChartFactory.createBarChart(
                    "Phân bố Điểm Trung Bình",
                    "Khoảng Điểm", "Số Sinh Viên",
                    dataset, PlotOrientation.VERTICAL,
                    false, true, false);

            // Styling
            chart.setBackgroundPaint(Color.WHITE);
            CategoryPlot plot = chart.getCategoryPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setRangeGridlinePaint(Constants.BORDER);
            BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, Constants.PRIMARY);

            panelChart1.removeAll();
            ChartPanel chartPanel = new ChartPanel(chart);
            panelChart1.add(chartPanel, BorderLayout.CENTER);
            panelChart1.revalidate();
            panelChart1.repaint();

        } catch (Exception e) {
            System.err.println("Lỗi tạo histogram: " + e.getMessage());
        }
    }

    // Biểu đồ tròn: Phân bố sinh viên theo lớp
    private void createClassDistribution() {
        try {
            List<SinhVien> allStudents = dao.getAllSinhVien();
            DefaultPieDataset dataset = new DefaultPieDataset();

            // Đếm số SV mỗi lớp
            Map<String, Integer> classCount = new LinkedHashMap<>();
            for (SinhVien sv : allStudents) {
                classCount.merge(sv.getLop(), 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : classCount.entrySet()) {
                dataset.setValue(entry.getKey() + " (" + entry.getValue() + " SV)",
                        entry.getValue());
            }

            JFreeChart chart = ChartFactory.createPieChart(
                    "Phân bố Sinh Viên theo Lớp",
                    dataset, true, true, false);

            chart.setBackgroundPaint(Color.WHITE);

            panelChart2.removeAll();
            ChartPanel chartPanel = new ChartPanel(chart);
            panelChart2.add(chartPanel, BorderLayout.CENTER);
            panelChart2.revalidate();
            panelChart2.repaint();

        } catch (Exception e) {
            System.err.println("Lỗi tạo pie chart: " + e.getMessage());
        }
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Nimbus Look & Feel - hỗ trợ tô màu nút đúng cách
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                    // Dùng Look & Feel mặc định nếu lỗi
                }
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
