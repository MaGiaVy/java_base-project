import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CHƯƠNG 13: WEB API VÀ CLIENT (JAVA)
 * Ứng dụng tích hợp cả RESTful Web API Server (/api/students) và Giao diện Swing Client (HttpClient)
 * Hỗ trợ đầy đủ CRUD: GET (All & By ID), POST (201 Created), PUT (200 OK / 404), DELETE (204 No Content / 404)
 */
public class Chuong13WebApiApp extends JFrame {

    // =========================================================================
    // BƯỚC 1: MODEL STUDENT (Dùng chung cho cả Web API và Client)
    // =========================================================================
    public static class Student {
        private int id;
        private String fullName;
        private int age;

        public Student() {}

        public Student(int id, String fullName, int age) {
            this.id = id;
            this.fullName = fullName;
            this.age = age;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        public String toJson() {
            return String.format(
                "{\n  \"id\": %d,\n  \"fullName\": \"%s\",\n  \"age\": %d\n}",
                id, escapeJson(fullName), age
            );
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    // =========================================================================
    // TIỆN ÍCH XỬ LÝ JSON (Thay thế nhẹ nhàng cho Jackson để không cần cài jar)
    // =========================================================================
    public static class JsonHelper {
        public static String toJsonArray(List<Student> list) {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                Student s = list.get(i);
                sb.append(String.format("  { \"id\": %d, \"fullName\": \"%s\", \"age\": %d }",
                        s.getId(), Student.escapeJson(s.getFullName()), s.getAge()));
                if (i < list.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            return sb.toString();
        }

        public static Student fromJson(String json) {
            Student s = new Student();
            Matcher mId = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
            if (mId.find()) {
                s.setId(Integer.parseInt(mId.group(1)));
            }
            Matcher mName = Pattern.compile("\"fullName\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            if (mName.find()) {
                s.setFullName(mName.group(1));
            }
            Matcher mAge = Pattern.compile("\"age\"\\s*:\\s*(\\d+)").matcher(json);
            if (mAge.find()) {
                s.setAge(Integer.parseInt(mAge.group(1)));
            }
            return s;
        }

        public static List<Student> fromJsonArray(String jsonArray) {
            List<Student> result = new ArrayList<>();
            Matcher mObj = Pattern.compile("\\{[^{}]*\\}").matcher(jsonArray);
            while (mObj.find()) {
                result.add(fromJson(mObj.group()));
            }
            return result;
        }
    }

    // =========================================================================
    // BƯỚC 2 -> 6: WEB API SERVER (Mô phỏng StudentsController tại /api/students)
    // =========================================================================
    public static class EmbeddedStudentApiServer {
        private HttpServer server;
        private final int port;
        private final List<Student> students = new CopyOnWriteArrayList<>();

        public EmbeddedStudentApiServer(int port) {
            this.port = port;
            resetSampleData();
        }

        public void resetSampleData() {
            students.clear();
            students.add(new Student(1, "Nguyễn Văn An", 20));
            students.add(new Student(2, "Trần Thị Bình", 21));
            students.add(new Student(3, "Lê Minh Châu", 19));
        }

        public void start() throws IOException {
            if (server != null) return;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/students", new StudentsHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        public void stop() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }

        public boolean isRunning() {
            return server != null;
        }

        private class StudentsHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String method = exchange.getRequestMethod().toUpperCase();
                String path = exchange.getRequestURI().getPath();
                String body = readRequestBody(exchange);

                // Chuẩn hóa đường dẫn (bỏ dấu '/' ở cuối nếu có)
                if (path.endsWith("/") && path.length() > 1) {
                    path = path.substring(0, path.length() - 1);
                }

                try {
                    // 1. Endpoint gốc: /api/students (COLLECTION)
                    if (path.equals("/api/students")) {
                        if ("GET".equals(method)) {
                            // BƯỚC 3: GET tất cả sinh viên -> 200 OK + JSON Array
                            String jsonResp = JsonHelper.toJsonArray(students);
                            sendJsonResponse(exchange, 200, jsonResp, null);
                        } else if ("POST".equals(method)) {
                            // BƯỚC 4: POST thêm sinh viên mới -> 201 Created + Location Header
                            Student incoming = JsonHelper.fromJson(body);
                            if (incoming.getFullName() == null || incoming.getFullName().trim().isEmpty() || incoming.getAge() <= 0) {
                                sendJsonResponse(exchange, 400, "{\n  \"type\": \"BadRequest\",\n  \"title\": \"Dữ liệu gửi lên không hợp lệ (Thiếu tên hoặc tuổi <= 0)\"\n}", null);
                                return;
                            }
                            int nextId = students.isEmpty() ? 1 : students.stream().mapToInt(Student::getId).max().getAsInt() + 1;
                            incoming.setId(nextId);
                            students.add(incoming);

                            String location = "/api/students/" + nextId;
                            sendJsonResponse(exchange, 201, incoming.toJson(), location);
                        } else {
                            sendJsonResponse(exchange, 405, "{ \"error\": \"Method Not Allowed\" }", null);
                        }
                    }
                    // 2. Endpoint có tham số: /api/students/{id} (ITEM)
                    else if (path.startsWith("/api/students/")) {
                        String idPart = path.substring("/api/students/".length());
                        int id;
                        try {
                            id = Integer.parseInt(idPart);
                        } catch (NumberFormatException e) {
                            sendJsonResponse(exchange, 400, "{\n  \"type\": \"BadRequest\",\n  \"title\": \"ID trên URL phải là số nguyên\"\n}", null);
                            return;
                        }

                        Student existing = students.stream().filter(s -> s.getId() == id).findFirst().orElse(null);

                        if ("GET".equals(method)) {
                            // BƯỚC 3: GET theo ID -> 200 OK hoặc 404 Not Found
                            if (existing == null) {
                                sendJsonResponse(exchange, 404, "{\n  \"type\": \"NotFound\",\n  \"title\": \"Not Found - Id " + id + " không tồn tại\"\n}", null);
                            } else {
                                sendJsonResponse(exchange, 200, existing.toJson(), null);
                            }
                        } else if ("PUT".equals(method)) {
                            // BƯỚC 5: PUT cập nhật theo ID -> 200 OK hoặc 404 Not Found
                            if (existing == null) {
                                sendJsonResponse(exchange, 404, "{\n  \"type\": \"NotFound\",\n  \"title\": \"Không tìm thấy sinh viên có Id = " + id + "\"\n}", null);
                            } else {
                                Student updated = JsonHelper.fromJson(body);
                                if (updated.getFullName() == null || updated.getFullName().trim().isEmpty() || updated.getAge() <= 0) {
                                    sendJsonResponse(exchange, 400, "{\n  \"type\": \"BadRequest\",\n  \"title\": \"Họ tên hoặc tuổi cập nhật không hợp lệ\"\n}", null);
                                    return;
                                }
                                existing.setFullName(updated.getFullName());
                                existing.setAge(updated.getAge());
                                sendJsonResponse(exchange, 200, existing.toJson(), null);
                            }
                        } else if ("DELETE".equals(method)) {
                            // BƯỚC 6: DELETE xóa theo ID -> 204 No Content (Không trả về Body) hoặc 404 Not Found
                            if (existing == null) {
                                sendJsonResponse(exchange, 404, "{\n  \"type\": \"NotFound\",\n  \"title\": \"Không tìm thấy sinh viên có Id = " + id + " để xóa\"\n}", null);
                            } else {
                                students.remove(existing);
                                exchange.getResponseHeaders().set("Server", "Java-Embedded-WebAPI/13.0");
                                exchange.sendResponseHeaders(204, -1); // 204 No Content
                                exchange.close();
                            }
                        } else {
                            sendJsonResponse(exchange, 405, "{ \"error\": \"Method Not Allowed\" }", null);
                        }
                    } else {
                        sendJsonResponse(exchange, 404, "{ \"error\": \"Endpoint không tồn tại\" }", null);
                    }
                } catch (Exception ex) {
                    sendJsonResponse(exchange, 500, "{\n  \"type\": \"InternalServerError\",\n  \"title\": \"" + Student.escapeJson(ex.getMessage()) + "\"\n}", null);
                }
            }

            private String readRequestBody(HttpExchange exchange) throws IOException {
                try (InputStream is = exchange.getRequestBody()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody, String locationHeader) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().set("Server", "Java-Embedded-WebAPI/13.0");
                if (locationHeader != null) {
                    exchange.getResponseHeaders().set("Location", locationHeader);
                }
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }

    // =========================================================================
    // BƯỚC 7 -> 11: API CLIENT DÙNG HTTPCLIENT (JAVA 11+)
    // =========================================================================
    public static class ApiCallLog {
        public String rawRequest;
        public String rawResponse;
        public int statusCode;
        public String responseBody;
    }

    public static class StudentApiClient {
        private final HttpClient httpClient;
        private String baseUrl;

        public StudentApiClient(String baseUrl) {
            this.baseUrl = baseUrl;
            // BƯỚC 7: Cấu hình HttpClient dùng chung cho nhiều request
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        // BƯỚC 8: Client gọi GET /api/students
        public ApiCallLog getAllStudents() throws Exception {
            String url = baseUrl + "/api/students";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            return executeRequest("GET", url, null, request);
        }

        // BƯỚC 3 & 8: Client gọi GET /api/students/{id}
        public ApiCallLog getStudentById(int id) throws Exception {
            String url = baseUrl + "/api/students/" + id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            return executeRequest("GET", url, null, request);
        }

        // BƯỚC 11: Client thêm một Student bằng POST /api/students
        public ApiCallLog addStudent(Student newStudent) throws Exception {
            String url = baseUrl + "/api/students";
            // Không gửi ID trong body (ID do Web API tự tạo)
            String jsonBody = String.format(
                    "{\n  \"fullName\": \"%s\",\n  \"age\": %d\n}",
                    Student.escapeJson(newStudent.getFullName()), newStudent.getAge()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            return executeRequest("POST", url, jsonBody, request);
        }

        // BƯỚC 9: Client cập nhật một Student bằng PUT /api/students/{id}
        public ApiCallLog updateStudent(int studentId, Student updatedStudent) throws Exception {
            String url = baseUrl + "/api/students/" + studentId;
            // ID nằm trong URL, không cần gửi trong JSON Body
            String jsonBody = String.format(
                    "{\n  \"fullName\": \"%s\",\n  \"age\": %d\n}",
                    Student.escapeJson(updatedStudent.getFullName()), updatedStudent.getAge()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            return executeRequest("PUT", url, jsonBody, request);
        }

        // BƯỚC 10: Client xóa một Student bằng DELETE /api/students/{id}
        public ApiCallLog deleteStudent(int studentId) throws Exception {
            String url = baseUrl + "/api/students/" + studentId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();
            return executeRequest("DELETE", url, null, request);
        }

        private ApiCallLog executeRequest(String method, String fullUrl, String reqBody, HttpRequest request) throws Exception {
            ApiCallLog log = new ApiCallLog();
            URI uri = URI.create(fullUrl);

            StringBuilder reqSb = new StringBuilder();
            reqSb.append(method).append(" ").append(uri.getPath()).append(" HTTP/1.1\n");
            reqSb.append("Host: ").append(uri.getHost()).append(":").append(uri.getPort()).append("\n");
            request.headers().map().forEach((k, v) -> reqSb.append(k).append(": ").append(String.join(", ", v)).append("\n"));
            if (reqBody != null && !reqBody.isEmpty()) {
                reqSb.append("\n").append(reqBody);
            } else {
                reqSb.append("\n(Không có Body)");
            }
            log.rawRequest = reqSb.toString();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.statusCode = response.statusCode();
            log.responseBody = response.body();

            StringBuilder respSb = new StringBuilder();
            respSb.append("HTTP/1.1 ").append(log.statusCode).append(" ").append(getStatusText(log.statusCode)).append("\n");
            response.headers().map().forEach((k, v) -> {
                if (!k.startsWith(":")) {
                    respSb.append(k).append(": ").append(String.join(", ", v)).append("\n");
                }
            });
            respSb.append("\n"); // Blank line phân cách Header và Body
            if (log.responseBody != null && !log.responseBody.isEmpty()) {
                respSb.append(log.responseBody);
            } else {
                respSb.append("(Không có Body - 204 No Content)");
            }
            log.rawResponse = respSb.toString();

            return log;
        }

        public static String getStatusText(int code) {
            switch (code) {
                case 200: return "OK (Thành công)";
                case 201: return "Created (Tạo mới thành công)";
                case 204: return "No Content (Xóa thành công - Không có Body)";
                case 400: return "Bad Request (Dữ liệu không hợp lệ)";
                case 404: return "Not Found (Không tìm thấy Resource)";
                case 500: return "Internal Server Error (Lỗi Server)";
                default: return "HTTP Status " + code;
            }
        }
    }

    // =========================================================================
    // GIAO DIỆN NGƯỜI DÙNG SWING (HOÀN CHỈNH, TRỰC QUAN)
    // =========================================================================
    private static final int DEFAULT_PORT = 5000;
    private EmbeddedStudentApiServer apiServer;
    private StudentApiClient apiClient;

    // Thành phần giao diện
    private JLabel lblServerStatus;
    private JButton btnToggleServer;
    private JTextField txtBaseUrl;

    private JTextField txtId;
    private JTextField txtFullName;
    private JTextField txtAge;

    private JTable tblStudents;
    private DefaultTableModel tableModel;

    private JTextArea txtHttpRequest;
    private JTextArea txtHttpResponse;
    private JLabel lblStatusBadge;
    private JTextArea txtConsoleLog;

    public Chuong13WebApiApp() {
        setTitle("Bài 13: Web API và Client (Java HttpClient & RESTful CRUD /api/students)");
        setSize(1220, 760);
        setMinimumSize(new Dimension(1050, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        apiServer = new EmbeddedStudentApiServer(DEFAULT_PORT);
        apiClient = new StudentApiClient("http://localhost:" + DEFAULT_PORT);

        initUI();
        startEmbeddedServerAutomatically();
    }

    private void initUI() {
        JPanel mainContainer = new JPanel(new BorderLayout(10, 10));
        mainContainer.setBorder(new EmptyBorder(10, 12, 10, 12));
        mainContainer.setBackground(new Color(244, 247, 252));
        setContentPane(mainContainer);

        // 1. TOP BANNER: Header & Trạng thái Web API Server
        mainContainer.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. CENTER: Chia đôi màn hình (Trái: Quản lý CRUD + Bảng | Phải: HTTP Request/Response Inspector)
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftCrudPanel(), createRightInspectorPanel());
        centerSplit.setResizeWeight(0.55);
        centerSplit.setDividerLocation(650);
        centerSplit.setBorder(null);
        mainContainer.add(centerSplit, BorderLayout.CENTER);

        // 3. BOTTOM: Nhật ký hoạt động (Console Log)
        mainContainer.add(createBottomLogPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(new Color(13, 37, 76));
        header.setBorder(new EmptyBorder(12, 18, 12, 18));

        // Tiêu đề bên trái
        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        JLabel lblTitle = new JLabel("BÀI 13 • WEB API VÀ CLIENT (JAVA)");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Mô hình RESTful Resource (/api/students) • Trao đổi JSON • Gọi API bằng Java 11+ HttpClient");
        lblSub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lblSub.setForeground(new Color(180, 210, 255));
        titleBox.add(lblTitle);
        titleBox.add(lblSub);

        // Điều khiển Server bên phải
        JPanel serverBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        serverBox.setOpaque(false);

        JLabel lblUrl = new JLabel("Base URL:");
        lblUrl.setForeground(Color.WHITE);
        lblUrl.setFont(new Font("SansSerif", Font.BOLD, 12));

        txtBaseUrl = new JTextField("http://localhost:" + DEFAULT_PORT, 16);
        txtBaseUrl.setFont(new Font("Monospaced", Font.BOLD, 13));

        lblServerStatus = new JLabel(" ● SERVER ĐANG DỪNG ");
        lblServerStatus.setOpaque(true);
        lblServerStatus.setBackground(new Color(220, 53, 69));
        lblServerStatus.setForeground(Color.WHITE);
        lblServerStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblServerStatus.setBorder(new EmptyBorder(5, 8, 5, 8));

        btnToggleServer = new JButton("Bật/Tắt Web API");
        btnToggleServer.setFocusPainted(false);
        btnToggleServer.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnToggleServer.addActionListener(e -> toggleServer());

        JButton btnResetData = new JButton("Khôi phục dữ liệu mẫu");
        btnResetData.setFocusPainted(false);
        btnResetData.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnResetData.addActionListener(e -> {
            apiServer.resetSampleData();
            appendLog("Đã khôi phục 3 sinh viên mẫu trong bộ nhớ Web API Server.");
            callGetAllStudents();
        });

        serverBox.add(lblUrl);
        serverBox.add(txtBaseUrl);
        serverBox.add(lblServerStatus);
        serverBox.add(btnToggleServer);
        serverBox.add(btnResetData);

        header.add(titleBox, BorderLayout.WEST);
        header.add(serverBox, BorderLayout.EAST);
        return header;
    }

    private JPanel createLeftCrudPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.setOpaque(false);

        // --- Khu vực Form nhập liệu & Nút bấm HTTP Methods ---
        JPanel formContainer = new JPanel(new BorderLayout(0, 10));
        formContainer.setBackground(Color.WHITE);
        formContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 215, 235), 1),
                        " BẢNG ĐIỀU KHIỂN CLIENT (GỬI HTTP REQUEST ĐẾN /api/students) ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 13), new Color(13, 37, 76)
                ),
                new EmptyBorder(10, 12, 10, 12)
        ));

        // Các ô nhập Id, FullName, Age
        JPanel inputGrid = new JPanel(new GridBagLayout());
        inputGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblId = new JLabel("Mã SV (id - URL Param):");
        lblId.setFont(new Font("SansSerif", Font.BOLD, 13));
        inputGrid.add(lblId, gbc);

        gbc.gridx = 1; gbc.weightx = 0.25;
        txtId = new JTextField();
        txtId.setFont(new Font("SansSerif", Font.BOLD, 14));
        txtId.setToolTipText("Dùng cho GET theo ID, PUT (/api/students/{id}) và DELETE (/api/students/{id}). Khi POST không cần nhập.");
        inputGrid.add(txtId, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JLabel lblAge = new JLabel("Tuổi (age):");
        lblAge.setFont(new Font("SansSerif", Font.BOLD, 13));
        inputGrid.add(lblAge, gbc);

        gbc.gridx = 3; gbc.weightx = 0.25;
        txtAge = new JTextField();
        txtAge.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputGrid.add(txtAge, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblName = new JLabel("Họ và tên (fullName):");
        lblName.setFont(new Font("SansSerif", Font.BOLD, 13));
        inputGrid.add(lblName, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0.7;
        txtFullName = new JTextField();
        txtFullName.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputGrid.add(txtFullName, gbc);

        gbc.gridx = 3; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.2;
        JButton btnClear = new JButton("Xóa trắng ô nhập");
        btnClear.setFocusPainted(false);
        btnClear.addActionListener(e -> {
            txtId.setText("");
            txtFullName.setText("");
            txtAge.setText("");
            tblStudents.clearSelection();
        });
        inputGrid.add(btnClear, gbc);

        // Chú thích quy tắc REST theo đúng Slide
        JLabel lblHint = new JLabel("<html><i>💡 <b>Lưu ý chuẩn REST:</b> Khi <b>POST</b> (Thêm mới), ID do Server tự sinh. Khi <b>PUT/DELETE</b>, ID được truyền trên URL <code>/api/students/{id}</code>.</i></html>");
        lblHint.setForeground(new Color(90, 105, 125));
        lblHint.setBorder(new EmptyBorder(4, 6, 4, 6));

        // 5 Nút bấm tương ứng với các HTTP Endpoints
        JPanel btnPanel = new JPanel(new GridLayout(1, 5, 8, 0));
        btnPanel.setOpaque(false);

        JButton btnGetAll = createMethodButton("GET ALL", "/api/students", new Color(0, 102, 204));
        btnGetAll.addActionListener(e -> callGetAllStudents());

        JButton btnGetById = createMethodButton("GET BY ID", "/api/students/{id}", new Color(23, 162, 184));
        btnGetById.addActionListener(e -> callGetStudentById());

        JButton btnPost = createMethodButton("POST (Thêm)", "/api/students", new Color(40, 167, 69));
        btnPost.addActionListener(e -> callPostStudent());

        JButton btnPut = createMethodButton("PUT (Cập nhật)", "/api/students/{id}", new Color(230, 126, 34));
        btnPut.addActionListener(e -> callPutStudent());

        JButton btnDelete = createMethodButton("DELETE (Xóa)", "/api/students/{id}", new Color(220, 53, 69));
        btnDelete.addActionListener(e -> callDeleteStudent());

        btnPanel.add(btnGetAll);
        btnPanel.add(btnGetById);
        btnPanel.add(btnPost);
        btnPanel.add(btnPut);
        btnPanel.add(btnDelete);

        JPanel southForm = new JPanel(new BorderLayout(0, 6));
        southForm.setOpaque(false);
        southForm.add(lblHint, BorderLayout.NORTH);
        southForm.add(btnPanel, BorderLayout.CENTER);

        formContainer.add(inputGrid, BorderLayout.CENTER);
        formContainer.add(southForm, BorderLayout.SOUTH);

        // --- Bảng hiển thị Danh sách Sinh viên (Resource: STUDENTS) ---
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBackground(Color.WHITE);
        tableContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 215, 235), 1),
                        " DANH SÁCH RESOURCE: STUDENTS (NHẤN VÀO DÒNG ĐỂ CHỌN NHANH) ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 13), new Color(13, 37, 76)
                ),
                new EmptyBorder(8, 8, 8, 8)
        ));

        String[] cols = {"ID (Định danh)", "Họ và tên (fullName)", "Tuổi (age)"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblStudents = new JTable(tableModel);
        tblStudents.setRowHeight(30);
        tblStudents.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tblStudents.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        tblStudents.getTableHeader().setBackground(new Color(230, 238, 250));
        tblStudents.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Căn giữa cột ID và Tuổi
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tblStudents.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tblStudents.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tblStudents.getColumnModel().getColumn(0).setPreferredWidth(90);
        tblStudents.getColumnModel().getColumn(1).setPreferredWidth(320);
        tblStudents.getColumnModel().getColumn(2).setPreferredWidth(90);

        // Sự kiện click chọn dòng -> Đổ lên ô nhập
        tblStudents.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tblStudents.getSelectedRow() != -1) {
                int row = tblStudents.getSelectedRow();
                txtId.setText(String.valueOf(tableModel.getValueAt(row, 0)));
                txtFullName.setText(String.valueOf(tableModel.getValueAt(row, 1)));
                txtAge.setText(String.valueOf(tableModel.getValueAt(row, 2)));
            }
        });

        tableContainer.add(new JScrollPane(tblStudents), BorderLayout.CENTER);

        leftPanel.add(formContainer, BorderLayout.NORTH);
        leftPanel.add(tableContainer, BorderLayout.CENTER);
        return leftPanel;
    }

    private JButton createMethodButton(String methodTitle, String endpointSub, Color bg) {
        JButton btn = new JButton("<html><center><b>" + methodTitle + "</b><br><span style='font-size:9px;'>" + endpointSub + "</span></center></html>");
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(110, 46));
        return btn;
    }

    private JPanel createRightInspectorPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setOpaque(false);

        // Panel trên: Chi tiết HTTP REQUEST gửi đi
        JPanel reqPanel = new JPanel(new BorderLayout());
        reqPanel.setBackground(Color.WHITE);
        reqPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 215, 235), 1),
                        " 1. HTTP REQUEST (CLIENT GỬI LÊN WEB API) ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 13), new Color(0, 102, 204)
                ),
                new EmptyBorder(6, 8, 8, 8)
        ));

        txtHttpRequest = new JTextArea();
        txtHttpRequest.setEditable(false);
        txtHttpRequest.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtHttpRequest.setBackground(new Color(16, 28, 48));
        txtHttpRequest.setForeground(new Color(140, 220, 255));
        txtHttpRequest.setMargin(new Insets(8, 10, 8, 10));
        reqPanel.add(new JScrollPane(txtHttpRequest), BorderLayout.CENTER);

        // Panel dưới: Chi tiết HTTP RESPONSE trả về
        JPanel respPanel = new JPanel(new BorderLayout(0, 6));
        respPanel.setBackground(Color.WHITE);
        respPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 215, 235), 1),
                        " 2. HTTP RESPONSE (WEB API TRẢ VỀ CHO CLIENT) ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 13), new Color(40, 167, 69)
                ),
                new EmptyBorder(6, 8, 8, 8)
        ));

        JPanel statusHeader = new JPanel(new BorderLayout());
        statusHeader.setOpaque(false);
        lblStatusBadge = new JLabel(" STATUS CODE: Chưa có request ");
        lblStatusBadge.setOpaque(true);
        lblStatusBadge.setBackground(new Color(108, 117, 125));
        lblStatusBadge.setForeground(Color.WHITE);
        lblStatusBadge.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblStatusBadge.setBorder(new EmptyBorder(5, 10, 5, 10));
        statusHeader.add(lblStatusBadge, BorderLayout.CENTER);

        txtHttpResponse = new JTextArea();
        txtHttpResponse.setEditable(false);
        txtHttpResponse.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtHttpResponse.setBackground(new Color(16, 28, 48));
        txtHttpResponse.setForeground(new Color(165, 245, 175));
        txtHttpResponse.setMargin(new Insets(8, 10, 8, 10));

        respPanel.add(statusHeader, BorderLayout.NORTH);
        respPanel.add(new JScrollPane(txtHttpResponse), BorderLayout.CENTER);

        JSplitPane vSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reqPanel, respPanel);
        vSplit.setResizeWeight(0.45);
        vSplit.setDividerLocation(210);
        vSplit.setBorder(null);

        rightPanel.add(vSplit, BorderLayout.CENTER);
        return rightPanel;
    }

    private JPanel createBottomLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(Color.WHITE);
        logPanel.setPreferredSize(new Dimension(100, 125));
        logPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(200, 215, 235), 1),
                        " NHẬT KÝ HOẠT ĐỘNG (CONSOLE LOG) ",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 12), new Color(13, 37, 76)
                ),
                new EmptyBorder(4, 8, 4, 8)
        ));

        txtConsoleLog = new JTextArea();
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtConsoleLog.setBackground(new Color(248, 250, 252));
        logPanel.add(new JScrollPane(txtConsoleLog), BorderLayout.CENTER);
        return logPanel;
    }

    // =========================================================================
    // XỬ LÝ SỰ KIỆN GỌI API TỪ CLIENT (GET, POST, PUT, DELETE)
    // =========================================================================
    private void startEmbeddedServerAutomatically() {
        try {
            apiServer.start();
            updateServerStatusUI(true);
            appendLog("Đã khởi động Embedded Web API Server thành công tại http://localhost:" + DEFAULT_PORT + "/api/students");
            callGetAllStudents();
        } catch (IOException ex) {
            updateServerStatusUI(false);
            appendLog("Không thể khởi động Server trên cổng " + DEFAULT_PORT + ": " + ex.getMessage());
        }
    }

    private void toggleServer() {
        if (apiServer.isRunning()) {
            apiServer.stop();
            updateServerStatusUI(false);
            appendLog("Đã dừng Web API Server.");
        } else {
            try {
                apiServer.start();
                updateServerStatusUI(true);
                appendLog("Đã bật lại Web API Server tại cổng " + DEFAULT_PORT);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi bật Server: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateServerStatusUI(boolean running) {
        if (running) {
            lblServerStatus.setText(" ● WEB API ONLINE (PORT " + DEFAULT_PORT + ") ");
            lblServerStatus.setBackground(new Color(40, 167, 69));
        } else {
            lblServerStatus.setText(" ● WEB API OFFLINE ");
            lblServerStatus.setBackground(new Color(220, 53, 69));
        }
    }

    // 1. Gọi GET /api/students
    private void callGetAllStudents() {
        apiClient.setBaseUrl(txtBaseUrl.getText().trim());
        new SwingWorker<ApiCallLog, Void>() {
            @Override
            protected ApiCallLog doInBackground() throws Exception {
                return apiClient.getAllStudents();
            }

            @Override
            protected void done() {
                try {
                    ApiCallLog log = get();
                    displayHttpInspector(log);
                    if (log.statusCode == 200) {
                        List<Student> list = JsonHelper.fromJsonArray(log.responseBody);
                        refreshTable(list);
                        appendLog("GET /api/students -> 200 OK (Đọc thành công " + list.size() + " sinh viên)");
                    }
                } catch (Exception ex) {
                    handleClientException("GET /api/students", ex);
                }
            }
        }.execute();
    }

    // 2. Gọi GET /api/students/{id}
    private void callGetStudentById() {
        String idStr = txtId.getText().trim();
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã SV (id) cần tìm vào ô ID!", "Thiếu tham số {id}", JOptionPane.WARNING_MESSAGE);
            txtId.requestFocus();
            return;
        }
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Mã SV (id) phải là một số nguyên!", "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
            return;
        }

        apiClient.setBaseUrl(txtBaseUrl.getText().trim());
        new SwingWorker<ApiCallLog, Void>() {
            @Override
            protected ApiCallLog doInBackground() throws Exception {
                return apiClient.getStudentById(id);
            }

            @Override
            protected void done() {
                try {
                    ApiCallLog log = get();
                    displayHttpInspector(log);
                    if (log.statusCode == 200) {
                        Student s = JsonHelper.fromJson(log.responseBody);
                        txtId.setText(String.valueOf(s.getId()));
                        txtFullName.setText(s.getFullName());
                        txtAge.setText(String.valueOf(s.getAge()));
                        appendLog("GET /api/students/" + id + " -> 200 OK: Tìm thấy [" + s.getFullName() + ", tuổi " + s.getAge() + "]");
                    } else if (log.statusCode == 404) {
                        appendLog("GET /api/students/" + id + " -> 404 Not Found: Không tìm thấy sinh viên có ID = " + id);
                        JOptionPane.showMessageDialog(Chuong13WebApiApp.this,
                                "404 Not Found: Không tìm thấy sinh viên có ID = " + id,
                                "Kết quả GET theo ID", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleClientException("GET /api/students/" + id, ex);
                }
            }
        }.execute();
    }

    // 3. Gọi POST /api/students
    private void callPostStudent() {
        String fullName = txtFullName.getText().trim();
        String ageStr = txtAge.getText().trim();
        if (fullName.isEmpty() || ageStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Khi thêm mới (POST), vui lòng nhập đầy đủ Họ tên và Tuổi!\n(Không cần nhập ID vì ID do Web API tự tạo)", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Tuổi (age) phải là số nguyên hợp lệ!", "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Student newStudent = new Student(0, fullName, age);
        apiClient.setBaseUrl(txtBaseUrl.getText().trim());

        new SwingWorker<ApiCallLog, Void>() {
            @Override
            protected ApiCallLog doInBackground() throws Exception {
                return apiClient.addStudent(newStudent);
            }

            @Override
            protected void done() {
                try {
                    ApiCallLog log = get();
                    displayHttpInspector(log);
                    if (log.statusCode == 201) {
                        Student created = JsonHelper.fromJson(log.responseBody);
                        txtId.setText(String.valueOf(created.getId()));
                        appendLog("POST /api/students -> 201 Created: Đã tạo mới SV [ID=" + created.getId() + ", " + created.getFullName() + "]");
                        reloadTableQuietly();
                    } else {
                        appendLog("POST /api/students thất bại -> Mã lỗi: " + log.statusCode);
                        JOptionPane.showMessageDialog(Chuong13WebApiApp.this,
                                "Không thể thêm sinh viên (HTTP " + log.statusCode + ")\nXem chi tiết ở khung HTTP Response.",
                                "Lỗi POST", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleClientException("POST /api/students", ex);
                }
            }
        }.execute();
    }

    // 4. Gọi PUT /api/students/{id}
    private void callPutStudent() {
        String idStr = txtId.getText().trim();
        String fullName = txtFullName.getText().trim();
        String ageStr = txtAge.getText().trim();

        if (idStr.isEmpty() || fullName.isEmpty() || ageStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Để cập nhật (PUT), vui lòng chọn hoặc nhập đủ: ID (trên URL), Họ tên mới và Tuổi mới!", "Thiếu thông tin PUT", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id, age;
        try {
            id = Integer.parseInt(idStr);
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ID và Tuổi phải là số nguyên!", "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Student updated = new Student(id, fullName, age);
        apiClient.setBaseUrl(txtBaseUrl.getText().trim());

        new SwingWorker<ApiCallLog, Void>() {
            @Override
            protected ApiCallLog doInBackground() throws Exception {
                return apiClient.updateStudent(id, updated);
            }

            @Override
            protected void done() {
                try {
                    ApiCallLog log = get();
                    displayHttpInspector(log);
                    if (log.statusCode == 200) {
                        appendLog("PUT /api/students/" + id + " -> 200 OK: Cập nhật thành công!");
                        reloadTableQuietly();
                    } else if (log.statusCode == 404) {
                        appendLog("PUT /api/students/" + id + " -> 404 Not Found: Không tìm thấy ID = " + id);
                        JOptionPane.showMessageDialog(Chuong13WebApiApp.this,
                                "404 Not Found: Không tìm thấy sinh viên có ID = " + id + " để cập nhật!",
                                "Lỗi cập nhật (404)", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleClientException("PUT /api/students/" + id, ex);
                }
            }
        }.execute();
    }

    // 5. Gọi DELETE /api/students/{id}
    private void callDeleteStudent() {
        String idStr = txtId.getText().trim();
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên hoặc nhập ID cần xóa!", "Thiếu tham số {id}", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ID phải là một số nguyên!", "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn gửi DELETE /api/students/" + id + " để xóa sinh viên này?",
                "Xác nhận DELETE Resource", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        apiClient.setBaseUrl(txtBaseUrl.getText().trim());

        new SwingWorker<ApiCallLog, Void>() {
            @Override
            protected ApiCallLog doInBackground() throws Exception {
                return apiClient.deleteStudent(id);
            }

            @Override
            protected void done() {
                try {
                    ApiCallLog log = get();
                    displayHttpInspector(log);
                    if (log.statusCode == 204 || log.statusCode == 200) {
                        appendLog("DELETE /api/students/" + id + " -> " + log.statusCode + " No Content: Đã xóa thành công!");
                        txtId.setText("");
                        txtFullName.setText("");
                        txtAge.setText("");
                        reloadTableQuietly();
                    } else if (log.statusCode == 404) {
                        appendLog("DELETE /api/students/" + id + " -> 404 Not Found: Không tìm thấy ID = " + id);
                        JOptionPane.showMessageDialog(Chuong13WebApiApp.this,
                                "404 Not Found: Không tìm thấy sinh viên có ID = " + id + " để xóa!",
                                "Lỗi DELETE (404)", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleClientException("DELETE /api/students/" + id, ex);
                }
            }
        }.execute();
    }

    // Cập nhật lại bảng sau khi POST/PUT/DELETE mà không ghi đè màn hình Inspector của lệnh vừa chạy
    private void reloadTableQuietly() {
        new SwingWorker<List<Student>, Void>() {
            @Override
            protected List<Student> doInBackground() throws Exception {
                ApiCallLog log = apiClient.getAllStudents();
                if (log.statusCode == 200) {
                    return JsonHelper.fromJsonArray(log.responseBody);
                }
                return new ArrayList<>();
            }

            @Override
            protected void done() {
                try {
                    refreshTable(get());
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void refreshTable(List<Student> students) {
        tableModel.setRowCount(0);
        for (Student s : students) {
            tableModel.addRow(new Object[]{s.getId(), s.getFullName(), s.getAge()});
        }
    }

    private void displayHttpInspector(ApiCallLog log) {
        txtHttpRequest.setText(log.rawRequest);
        txtHttpRequest.setCaretPosition(0);

        txtHttpResponse.setText(log.rawResponse);
        txtHttpResponse.setCaretPosition(0);

        String statusDesc = StudentApiClient.getStatusText(log.statusCode);
        lblStatusBadge.setText(" STATUS CODE: " + log.statusCode + " - " + statusDesc);

        if (log.statusCode >= 200 && log.statusCode < 300) {
            lblStatusBadge.setBackground(new Color(40, 167, 69)); // 2xx Thành công (Xanh lá)
        } else if (log.statusCode >= 400 && log.statusCode < 500) {
            lblStatusBadge.setBackground(new Color(230, 126, 34)); // 4xx Lỗi từ Client (Cam)
        } else {
            lblStatusBadge.setBackground(new Color(220, 53, 69)); // 5xx Lỗi Server (Đỏ)
        }
    }

    private void handleClientException(String action, Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        appendLog("LỖI KẾT NỐI khi gọi " + action + ": " + cause.getMessage());
        JOptionPane.showMessageDialog(this,
                "Không thể kết nối tới Web API (" + txtBaseUrl.getText() + ").\nHãy kiểm tra xem Server đã bật chưa!\nChi tiết: " + cause.getMessage(),
                "Lỗi kết nối mạng", JOptionPane.ERROR_MESSAGE);
    }

    private void appendLog(String msg) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtConsoleLog.append("[" + time + "] " + msg + "\n");
        txtConsoleLog.setCaretPosition(txtConsoleLog.getDocument().getLength());
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            Chuong13WebApiApp app = new Chuong13WebApiApp();
            app.setVisible(true);
        });
    }
}