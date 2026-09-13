# 🎨 PROMPT: Thiết Kế UI Modern - Java Swing

## 📋 TASK

Viết code Java Swing để tạo giao diện **quản lý sinh viên** với design **modern, clean, professional**. Giao diện phải có:

- ✅ Color scheme đẹp (Material Design)
- ✅ Layout rõ ràng, cân bằng
- ✅ Icons + Typography hợp lý
- ✅ Tab navigation (4 tabs)
- ✅ Charts (Histogram, Pie, Line)
- ✅ Responsive, không bị lag

---

## 🎯 REQUIREMENTS

### **1. Color Palette**

```
Primary Blue:       #2196F3
Accent Orange:      #FF5722
Success Green:      #4CAF50
Warning Yellow:     #FFC107
Danger Red:         #F44336
Light Background:   #F5F5F5
Dark Text:          #212121
Border Gray:        #BDBDBD
```

### **2. Typography**

- **Title (JFrame):** Segoe UI, 18px, Bold
- **Tab Headers:** Segoe UI, 12px, Bold
- **Labels:** Segoe UI, 11px, Regular
- **Buttons:** Segoe UI, 11px, Bold

### **3. UI Components**

#### **Tab 1: Danh Sách Sinh Viên**

```
- Top bar:
  * [🔄 Refresh] [📥 Import] [📤 Export]
- Table:
  * Columns: Mã SV | Họ Tên | Lớp | Ngày Sinh | Điểm TB
  * Sortable (click header)
  * Selectable rows (highlight blue)
- Status bar: "Tổng: XX sinh viên"
```

#### **Tab 2: Thêm/Sửa Sinh Viên**

```
- Form Panel:
  * Mã SV:* [TextBox] (disabled khi edit)
  * Họ tên:* [TextBox]
  * Lớp:* [ComboBox]
  * Ngày sinh:* [DatePicker]
  * Điểm TB:* [Spinner 0-10]

- Buttons:
  * [✅ Lưu] (green) - SaveButton
  * [🔄 Làm mới] (blue) - ClearButton
  * [❌ Hủy] (red) - CancelButton

- Validation:
  * Show red border nếu field sai
  * Error message dưới field
```

#### **Tab 3: Tìm Kiếm**

```
- Search Bar:
  * [Search icon] [TextBox "Nhập từ khóa"] [🔍 Button]

- Filters:
  * ☐ Mã SV  ☐ Họ tên  ☐ Lớp

- Results:
  * Table hiển thị kết quả
  * "XX kết quả tìm được"

- Actions:
  * [📋 A-Z] [📊 Theo Điểm] [🔄 Reset]
```

#### **Tab 4: Thống Kê**

```
- Left Panel (Summary):
  * 👥 Tổng SV: [Bold large number]
  * 📈 Điểm TB: [Number]/10
  * 🏆 Cao nhất: [Name (Score)]
  * 📊 Top 5 SV: [List]

- Right Panel (Charts):
  * Chart 1 (Histogram): Phân bố điểm
  * Chart 2 (Pie): Phân bố lớp
  * Chart 3 (Line): Xu hướng điểm
```

---

## 🖌️ DESIGN GUIDELINES

### **Spacing & Padding**

```
- Margin frame: 15px
- Panel padding: 10px
- Component gap: 8px
- Button padding: 8px x 12px
```

### **Borders & Shadows**

```
- Use:
  * LineBorder (1px) cho input fields
  * TitledBorder cho panels
  * Subtle shadow cho cards (optional)

- Colors:
  * Default border: #BDBDBD
  * Focus border: #2196F3
  * Error border: #F44336
```

### **Button Styling**

```
- Normal:
  * Background: Primary color
  * Foreground: White
  * Border: Rounded corners
  * Font: Bold

- Hover:
  * Background: Darker shade
  * Cursor: Hand

- Disabled:
  * Background: #BDBDBD
  * Foreground: #666666
```

### **Table Styling**

```
- Header:
  * Background: #2196F3
  * Foreground: White
  * Bold font

- Rows:
  * Alternate: White / #F5F5F5
  * Selected: #2196F3 (light) background
  * Row height: 25px

- Columns:
  * Auto-resize
  * Sortable (click header)
```

### **Icons**

```
Sử dụng Unicode hoặc IconLib:
- 📋 List
- ➕ Add
- ✏️ Edit
- 🗑️ Delete
- 🔍 Search
- 📊 Stats
- 📊 Chart
- ✅ Save
- ❌ Cancel
- 🔄 Refresh
```

---

## 💻 CODE STRUCTURE

### **Class Layout**

```
MainFrame.java
├── initComponents()          // Tạo UI
├── initTab1_List()          // Tab Danh sách
├── initTab2_AddEdit()       // Tab Thêm/Sửa
├── initTab3_Search()        // Tab Tìm kiếm
├── initTab4_Statistics()    // Tab Thống kê
├── setupColors()            // Set color scheme
├── createStyles()           // Font, border, etc
└── updateUI()               // Refresh khi thay đổi dữ liệu
```

### **Main Components**

```
MainFrame (JFrame)
├── JMenuBar (File, Help)
├── JTabbedPane
│   ├── Tab 1: JPanel (BorderLayout)
│   │   ├── JToolBar (buttons)
│   │   └── JScrollPane → JTable
│   ├── Tab 2: JPanel (GridBagLayout)
│   │   ├── Form fields
│   │   └── Buttons
│   ├── Tab 3: JPanel (BorderLayout)
│   │   ├── Search controls
│   │   └── JTable results
│   └── Tab 4: JPanel (BorderLayout)
│       ├── Left: JPanel (summary)
│       └── Right: JPanel (charts)
└── JStatusBar (info)
```

---

## 🎬 ADVANCED FEATURES (Optional)

### **1. Custom Look & Feel**

```java
UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
// Hoặc Flatlaf
UIManager.setLookAndFeel(new FlatLightLaf());
```

### **2. Smooth Transitions**

```java
// Fade in/out khi switch tab
// Animate button click
```

### **3. Dark Mode Toggle**

```java
// Button switch theme: Light/Dark
```

### **4. Responsive Layout**

```java
// Resize components khi window resize
// GridBagLayout or MigLayout
```

---

## 📝 DELIVERABLES

Khi viết code, bao gồm:

1. ✅ MainFrame.java (main UI class)
2. ✅ Constants.java (colors, fonts, sizes)
3. ✅ UIHelper.java (helper methods)
4. ✅ Full code có comment rõ ràng
5. ✅ Ready to integrate với SinhVienDAO
6. ✅ No hardcoded values (use constants)

---

## 🚀 BONUS POINTS

- [ ] Custom JPanel components (CardPanel, StatPanel)
- [ ] Animation on data load
- [ ] Undo/Redo functionality
- [ ] Preferences/Settings window
- [ ] Export to PDF
- [ ] Multi-language support (EN/VI)
- [ ] Keyboard shortcuts
- [ ] Toast notifications (JtoastPanel)

---

**Prompt này là blueprint để AI tạo giao diện Swing tuyệt đẹp! 🎨**
