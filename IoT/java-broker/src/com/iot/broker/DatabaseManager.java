package com.iot.broker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ============================================================================
 * LỚP DatabaseManager - QUẢN LÝ KẾT NỐI VÀ LƯU TRỮ CƠ SỞ DỮ LIỆU POSTGRESQL
 * ============================================================================
 * 
 * [CHỨC NĂNG CHÍNH]:
 * 1. Thiết lập kết nối JDBC tới máy chủ PostgreSQL (mặc định localhost:5432, db: postgres).
 * 2. Tự động kiểm tra và khởi tạo 2 bảng dữ liệu nếu chưa tồn tại:
 *    - sensor_data: Lưu trữ lịch sử nhiệt độ, độ ẩm từ ESP32.
 *    - actuator_logs: Lưu trữ lịch sử điều khiển bật/tắt đèn LED.
 * 3. Chạy luồng ghi ngầm (Background Executor) bất đồng bộ:
 *    - Đảm bảo việc ghi dữ liệu vào ổ cứng không bao giờ làm nghẽn luồng mạng của MQTT Broker.
 * 4. Cung cấp hàm truy vấn lịch sử mới nhất để hiển thị trực quan lên giao diện Java BrokerFrame.
 * 5. Xuất báo cáo dữ liệu trực tiếp ra file Microsoft Word (.docx) chuẩn XML nộp cho giảng viên.
 */
public class DatabaseManager {

    // ========================================================================
    // 1. CÁC THÔNG SỐ CẤU HÌNH KẾT NỐI POSTGRESQL
    // ========================================================================
    private String host = "localhost";
    private int port = 5432;
    private String dbName = "postgres";
    private String user = "postgres";
    private String password = "M@giavy265";

    // Đối tượng kết nối JDBC duy nhất tới PostgreSQL
    private Connection connection = null;

    // Trạng thái kết nối hiện tại (true: đang kết nối tốt, false: mất kết nối hoặc lỗi)
    private volatile boolean connected = false;

    // Lưu chuỗi lỗi gần nhất để hiển thị lên thanh trạng thái giao diện
    private String lastError = "";

    // Luồng đơn chuyên trách ghi dữ liệu ngầm (Queue tuần tự, không nghẽn Broker)
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // ========================================================================
    // 2. ĐỊNH NGHĨA CẤU TRÚC DỮ LIỆU ĐỂ HIỂN THỊ VÀ TRUY VẤN
    // ========================================================================

    /**
     * Bản ghi dữ liệu cảm biến DHT22
     */
    public static class SensorRecord {
        public long id;
        public String deviceId;
        public double temperature;
        public double humidity;
        public double heatIndex;
        public String topic;
        public String recordedAt;

        public SensorRecord(long id, String deviceId, double temp, double hum, double heatIndex, String topic, String recordedAt) {
            this.id = id;
            this.deviceId = deviceId;
            this.temperature = temp;
            this.humidity = hum;
            this.heatIndex = heatIndex;
            this.topic = topic;
            this.recordedAt = recordedAt;
        }
    }

    /**
     * Bản ghi lịch sử điều khiển đèn LED
     */
    public static class ActuatorRecord {
        public long id;
        public String deviceId;
        public String action;
        public String triggeredBy;
        public String topic;
        public String recordedAt;

        public ActuatorRecord(long id, String deviceId, String action, String triggeredBy, String topic, String recordedAt) {
            this.id = id;
            this.deviceId = deviceId;
            this.action = action;
            this.triggeredBy = triggeredBy;
            this.topic = topic;
            this.recordedAt = recordedAt;
        }
    }

    // ========================================================================
    // 3. KHỞI TẠO VÀ KẾT NỐI TỚI POSTGRESQL
    // ========================================================================

    public DatabaseManager() {
        // Tải PostgreSQL JDBC Driver vào bộ nhớ JVM
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            lastError = "Chưa tìm thấy thư viện postgresql.jar trong classpath!";
            System.err.println("[DatabaseManager] " + lastError);
        }
    }

    /**
     * Kết nối tới cơ sở dữ liệu với các thông số truyền vào.
     * Tự động khởi tạo bảng nếu kết nối thành công.
     */
    public synchronized boolean connect(String host, int port, String dbName, String user, String password) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.user = user;
        this.password = password;

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?connectTimeout=5&socketTimeout=10";

        try {
            // Đóng kết nối cũ nếu có
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            // Thiết lập kết nối JDBC mới
            connection = DriverManager.getConnection(jdbcUrl, user, password);
            connected = true;
            lastError = "";
            System.out.println("[DatabaseManager] KẾT NỐI THÀNH CÔNG tới PostgreSQL: " + jdbcUrl);

            // Tự động tạo các bảng nếu chưa có
            createTablesIfNotExists();
            return true;

        } catch (SQLException e) {
            connected = false;
            lastError = e.getMessage();
            System.err.println("[DatabaseManager] LỖI KẾT NỐI POSTGRESQL: " + lastError);
            return false;
        }
    }

    /**
     * Ngắt kết nối PostgreSQL an toàn khi tắt Broker
     */
    public synchronized void disconnect() {
        connected = false;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DatabaseManager] Đã ngắt kết nối PostgreSQL an toàn.");
            }
        } catch (SQLException ignored) {
        }
    }

    /**
     * Kiểm tra trạng thái kết nối
     */
    public boolean isConnected() {
        try {
            if (connected && connection != null && !connection.isClosed()) {
                return true;
            }
        } catch (SQLException e) {
            connected = false;
        }
        return false;
    }

    public String getLastError() {
        return lastError;
    }

    // ========================================================================
    // 4. TỰ ĐỘNG TẠO BẢNG TRONG DATABASE NẾU CHƯA CÓ
    // ========================================================================
    private void createTablesIfNotExists() {
        if (!isConnected()) return;

        // Câu lệnh SQL tạo bảng sensor_data
        String sqlCreateSensorTable = 
            "CREATE TABLE IF NOT EXISTS sensor_data (" +
            "    id SERIAL PRIMARY KEY," +
            "    device_id VARCHAR(50) DEFAULT 'ESP32-DHT22'," +
            "    temperature DOUBLE PRECISION NOT NULL," +
            "    humidity DOUBLE PRECISION NOT NULL," +
            "    heat_index DOUBLE PRECISION," +
            "    topic VARCHAR(100) NOT NULL," +
            "    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ");";

        // Câu lệnh SQL tạo bảng actuator_logs
        String sqlCreateActuatorTable = 
            "CREATE TABLE IF NOT EXISTS actuator_logs (" +
            "    id SERIAL PRIMARY KEY," +
            "    device_id VARCHAR(50) DEFAULT 'ESP32-LED'," +
            "    action VARCHAR(20) NOT NULL," +
            "    triggered_by VARCHAR(50)," +
            "    topic VARCHAR(100) NOT NULL," +
            "    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlCreateSensorTable);
            stmt.execute(sqlCreateActuatorTable);
            System.out.println("[DatabaseManager] Đã kiểm tra và đồng bộ cấu trúc 2 bảng sensor_data & actuator_logs!");
        } catch (SQLException e) {
            lastError = "Lỗi tạo bảng: " + e.getMessage();
            System.err.println("[DatabaseManager] " + lastError);
        }
    }

    // ========================================================================
    // 5. CÁC HÀM GHI DỮ LIỆU BẤT ĐỒNG BỘ (ASYNC BACKGROUND WRITING)
    // ========================================================================

    /**
     * Lưu bản tin nhiệt độ - độ ẩm vào bảng sensor_data.
     * Chạy bất đồng bộ qua dbExecutor để Broker không bị gián đoạn.
     */
    public void saveSensorDataAsync(String deviceId, double temp, double hum, double heatIndex, String topic) {
        dbExecutor.submit(() -> {
            if (!isConnected()) {
                // Thử kết nối lại tự động nếu bị mất kết nối tạm thời
                connect(host, port, dbName, user, password);
                if (!isConnected()) return;
            }

            String sql = "INSERT INTO sensor_data (device_id, temperature, humidity, heat_index, topic, recorded_at) " +
                         "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, deviceId != null ? deviceId : "ESP32-DHT22");
                ps.setDouble(2, temp);
                ps.setDouble(3, hum);
                ps.setDouble(4, heatIndex);
                ps.setString(5, topic != null ? topic : "esp32/sensor/dht22/data");
                ps.executeUpdate();
            } catch (SQLException e) {
                lastError = "Lỗi ghi sensor_data: " + e.getMessage();
                System.err.println("[DatabaseManager] " + lastError);
            }
        });
    }

    /**
     * Lưu nhật ký điều khiển đèn LED vào bảng actuator_logs.
     */
    public void saveActuatorLogAsync(String deviceId, String action, String triggeredBy, String topic) {
        dbExecutor.submit(() -> {
            if (!isConnected()) {
                connect(host, port, dbName, user, password);
                if (!isConnected()) return;
            }

            String sql = "INSERT INTO actuator_logs (device_id, action, triggered_by, topic, recorded_at) " +
                         "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, deviceId != null ? deviceId : "ESP32-LED");
                ps.setString(2, action != null ? action.toUpperCase() : "TOGGLE");
                ps.setString(3, triggeredBy != null ? triggeredBy : "CLIENT");
                ps.setString(4, topic != null ? topic : "esp32/actuator/led/control");
                ps.executeUpdate();
            } catch (SQLException e) {
                lastError = "Lỗi ghi actuator_logs: " + e.getMessage();
                System.err.println("[DatabaseManager] " + lastError);
            }
        });
    }

    // ========================================================================
    // 6. CÁC HÀM TRUY VẤN LỊCH SỬ DỮ LIỆU ĐỂ HIỂN THỊ LÊN GIAO DIỆN
    // ========================================================================

    /**
     * Lấy N bản ghi cảm biến mới nhất từ cơ sở dữ liệu (xếp giảm dần theo thời gian)
     */
    public synchronized List<SensorRecord> getRecentSensorData(int limit) {
        List<SensorRecord> list = new ArrayList<>();
        if (!isConnected()) return list;

        String sql = "SELECT id, device_id, temperature, humidity, heat_index, topic, " +
                     "TO_CHAR(recorded_at, 'YYYY-MM-DD HH24:MI:SS') as rec_time " +
                     "FROM sensor_data ORDER BY id DESC LIMIT ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SensorRecord(
                        rs.getLong("id"),
                        rs.getString("device_id"),
                        rs.getDouble("temperature"),
                        rs.getDouble("humidity"),
                        rs.getDouble("heat_index"),
                        rs.getString("topic"),
                        rs.getString("rec_time")
                    ));
                }
            }
        } catch (SQLException e) {
            lastError = "Lỗi đọc sensor_data: " + e.getMessage();
            System.err.println("[DatabaseManager] " + lastError);
        }
        return list;
    }

    /**
     * Lấy N bản ghi điều khiển đèn LED mới nhất
     */
    public synchronized List<ActuatorRecord> getRecentActuatorLogs(int limit) {
        List<ActuatorRecord> list = new ArrayList<>();
        if (!isConnected()) return list;

        String sql = "SELECT id, device_id, action, triggered_by, topic, " +
                     "TO_CHAR(recorded_at, 'YYYY-MM-DD HH24:MI:SS') as rec_time " +
                     "FROM actuator_logs ORDER BY id DESC LIMIT ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActuatorRecord(
                        rs.getLong("id"),
                        rs.getString("device_id"),
                        rs.getString("action"),
                        rs.getString("triggered_by"),
                        rs.getString("topic"),
                        rs.getString("rec_time")
                    ));
                }
            }
        } catch (SQLException e) {
            lastError = "Lỗi đọc actuator_logs: " + e.getMessage();
            System.err.println("[DatabaseManager] " + lastError);
        }
        return list;
    }

    // ========================================================================
    // 7. XUẤT BÁO CÁO DỮ LIỆU RA FILE MICROSOFT WORD (.DOCX)
    // ========================================================================

    /**
     * Tạo file Word (.docx) chuẩn XML chứa bảng biểu dữ liệu lịch sử đo đạc
     * và các chỉ số thống kê (Max, Min, Trung bình) để nộp cho giảng viên.
     * Sử dụng ZipOutputStream thuần Java (không cần thư viện ngoài).
     */
    public boolean exportToDocx(File targetFile, List<SensorRecord> records) {
        if (targetFile == null || records == null) return false;

        // Tính toán thống kê cơ bản
        int count = records.size();
        double minTemp = Double.MAX_VALUE;
        double maxTemp = Double.MIN_VALUE;
        double sumTemp = 0.0;
        double minHum = Double.MAX_VALUE;
        double maxHum = Double.MIN_VALUE;
        double sumHum = 0.0;

        for (SensorRecord r : records) {
            if (r.temperature < minTemp) minTemp = r.temperature;
            if (r.temperature > maxTemp) maxTemp = r.temperature;
            sumTemp += r.temperature;

            if (r.humidity < minHum) minHum = r.humidity;
            if (r.humidity > maxHum) maxHum = r.humidity;
            sumHum += r.humidity;
        }

        double avgTemp = count > 0 ? (sumTemp / count) : 0.0;
        double avgHum = count > 0 ? (sumHum / count) : 0.0;
        if (count == 0) {
            minTemp = 0.0; maxTemp = 0.0;
            minHum = 0.0; maxHum = 0.0;
        }

        String exportTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // Tạo nội dung file word/document.xml theo chuẩn WordprocessingML
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        xml.append("<w:body>\n");

        // Tiêu đề báo cáo
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>");
        xml.append("<w:r><w:rPr><w:b/><w:sz w:val=\"36\"/><w:color w:val=\"1E3A8A\"/></w:rPr>");
        xml.append("<w:t>BÁO CÁO GIÁM SÁT DỮ LIỆU IOT - NHIỆT ĐỘ &amp; ĐỘ ẨM</w:t></w:r></w:p>\n");

        // Phụ đề
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>");
        xml.append("<w:r><w:rPr><w:i/><w:sz w:val=\"22\"/><w:color w:val=\"4B5563\"/></w:rPr>");
        xml.append("<w:t>Hệ thống giám sát ESP32 DHT22 - Lưu trữ Cơ sở dữ liệu PostgreSQL</w:t></w:r></w:p>\n");

        // Ngày giờ xuất
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>");
        xml.append("<w:r><w:rPr><w:sz w:val=\"20\"/><w:color w:val=\"6B7280\"/></w:rPr>");
        xml.append("<w:t>Thời gian kết xuất dữ liệu: ").append(exportTime).append("</w:t></w:r></w:p>\n");
        xml.append("<w:p/>\n"); // Dòng trống

        // Mục 1: Thống kê tổng quan
        xml.append("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"26\"/><w:color w:val=\"111827\"/></w:rPr>");
        xml.append("<w:t>1. Tổng quan Thống kê Dữ liệu</w:t></w:r></w:p>\n");

        xml.append(buildParagraphBullet("Tổng số mẫu đo ghi nhận: " + count + " bản ghi"));
        xml.append(buildParagraphBullet(String.format("Nhiệt độ trung bình: %.1f °C (Thấp nhất: %.1f °C, Cao nhất: %.1f °C)", avgTemp, minTemp, maxTemp)));
        xml.append(buildParagraphBullet(String.format("Độ ẩm trung bình: %.1f %% (Thấp nhất: %.1f %%, Cao nhất: %.1f %%)", avgHum, minHum, maxHum)));
        xml.append(buildParagraphBullet("Nguồn thu thập: ESP32 Simulator (Wokwi) qua giao thức MQTT"));
        xml.append(buildParagraphBullet("Hệ quản trị CSDL: PostgreSQL 18 (Bảng sensor_data)"));
        xml.append("<w:p/>\n");

        // Mục 2: Bảng chi tiết các bản ghi
        xml.append("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"26\"/><w:color w:val=\"111827\"/></w:rPr>");
        xml.append("<w:t>2. Danh sách Chi tiết Các Bản ghi Đo đạc</w:t></w:r></w:p>\n");

        // Tạo bảng Word
        xml.append("<w:tbl>\n");
        xml.append("<w:tblPr>");
        xml.append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>");
        xml.append("<w:tblBorders>");
        xml.append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CCCCCC\"/>");
        xml.append("<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CCCCCC\"/>");
        xml.append("<w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CCCCCC\"/>");
        xml.append("<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CCCCCC\"/>");
        xml.append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E5E7EB\"/>");
        xml.append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E5E7EB\"/>");
        xml.append("</w:tblBorders>");
        xml.append("</w:tblPr>\n");

        // Hàng tiêu đề bảng (Header Row)
        xml.append("<w:tr>");
        xml.append(buildTableCell("ID", true, "3B82F6", true));
        xml.append(buildTableCell("Thời gian ghi", true, "3B82F6", true));
        xml.append(buildTableCell("Thiết bị", true, "3B82F6", true));
        xml.append(buildTableCell("Nhiệt độ (°C)", true, "3B82F6", true));
        xml.append(buildTableCell("Độ ẩm (%)", true, "3B82F6", true));
        xml.append(buildTableCell("Chỉ số nhiệt (°C)", true, "3B82F6", true));
        xml.append(buildTableCell("MQTT Topic", true, "3B82F6", true));
        xml.append("</w:tr>\n");

        // Các hàng dữ liệu (Data Rows)
        for (SensorRecord r : records) {
            xml.append("<w:tr>");
            xml.append(buildTableCell(String.valueOf(r.id), false, null, true));
            xml.append(buildTableCell(r.recordedAt, false, null, false));
            xml.append(buildTableCell(r.deviceId, false, null, false));
            xml.append(buildTableCell(String.format("%.1f", r.temperature), false, null, true));
            xml.append(buildTableCell(String.format("%.1f", r.humidity), false, null, true));
            xml.append(buildTableCell(String.format("%.1f", r.heatIndex), false, null, true));
            xml.append(buildTableCell(r.topic, false, null, false));
            xml.append("</w:tr>\n");
        }
        xml.append("</w:tbl>\n");

        // Kết thúc tài liệu Word
        xml.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>\n");
        xml.append("</w:body></w:document>");

        // Đóng gói thành file .docx (ZIP archive)
        return writeDocxArchive(targetFile, xml.toString());
    }

    private static String buildParagraphBullet(String text) {
        return "<w:p><w:pPr><w:pStyle w:val=\"ListParagraph\"/></w:pPr>" +
               "<w:r><w:rPr><w:sz w:val=\"22\"/></w:rPr>" +
               "<w:t>• " + escapeXml(text) + "</w:t></w:r></w:p>\n";
    }

    private static String buildTableCell(String text, boolean isHeader, String bgColor, boolean center) {
        StringBuilder sb = new StringBuilder();
        sb.append("<w:tc><w:tcPr>");
        if (bgColor != null) {
            sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(bgColor).append("\"/>");
        }
        sb.append("</w:tcPr>");
        sb.append("<w:p><w:pPr>");
        if (center) {
            sb.append("<w:jc w:val=\"center\"/>");
        }
        sb.append("</w:pPr><w:r><w:rPr>");
        if (isHeader) {
            sb.append("<w:b/><w:color w:val=\"FFFFFF\"/>");
        }
        sb.append("<w:sz w:val=\"20\"/></w:rPr>");
        sb.append("<w:t>").append(escapeXml(text)).append("</w:t></w:r></w:p></w:tc>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * Nén các file XML cấu trúc chuẩn của OpenXML (.docx) vào file ZIP
     */
    private static boolean writeDocxArchive(File targetFile, String documentXmlContent) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetFile))) {
            
            // 1. [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            byte[] contentTypes = (
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                "</Types>"
            ).getBytes(StandardCharsets.UTF_8);
            zos.write(contentTypes);
            zos.closeEntry();

            // 2. _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            byte[] rels = (
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                "</Relationships>"
            ).getBytes(StandardCharsets.UTF_8);
            zos.write(rels);
            zos.closeEntry();

            // 3. word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] docXml = documentXmlContent.getBytes(StandardCharsets.UTF_8);
            zos.write(docXml);
            zos.closeEntry();

            System.out.println("[DatabaseManager] Xuất file Word (.docx) thành công: " + targetFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            System.err.println("[DatabaseManager] Lỗi xuất file Word: " + e.getMessage());
            return false;
        }
    }
}
