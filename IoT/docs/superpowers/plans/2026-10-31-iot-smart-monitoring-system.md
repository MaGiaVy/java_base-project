# Hệ Thống Giám Sát IoT & Điều Khiển Thiết Bị Qua MQTT (Wokwi ESP32 - Mosquitto - Web Gauge - Java Swing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hệ thống IoT hoàn chỉnh gồm mạch mô phỏng ESP32 (DHT22 + LED) trên Wokwi, Mosquitto MQTT Broker (hỗ trợ cả TCP 1883 và WebSockets 9001 có xác thực), Web Dashboard đo thông số dạng Gauge xoay + biểu đồ realtime, và ứng dụng Java Swing Client điều khiển 2 chiều & xuất báo cáo CSV.

**Architecture:** Mạch ESP32 trên Wokwi đóng vai trò Edge Device thu thập nhiệt độ/độ ẩm từ DHT22 và publish lên MQTT Broker theo định dạng JSON, đồng thời subscribe nhận lệnh điều khiển LED. Mosquitto Broker là máy chủ điều phối tin nhắn trung tâm (mở đồng thời TCP 1883 cho ESP32/Java và WebSocket 9001 cho Web). Web Dashboard (HTML/JS) và Java Desktop Client (Swing GUI) cùng nhận dữ liệu thời gian thực và cùng có thể gửi lệnh bật/tắt đèn LED tới ESP32.

**Tech Stack:**
- Embedded: ESP32, C++/Arduino (Wokwi Simulator), Thư viện `PubSubClient`, `DHTesp`, `ArduinoJson`.
- Broker: Eclipse Mosquitto (TCP `1883`, WebSockets `9001`, xác thực `user1` / `123456`, hỗ trợ chuyển đổi sang Public Broker `broker.emqx.io`).
- Frontend: HTML5, CSS3, JavaScript, `mqtt.js` (WebSockets), Canvas Gauge Meter, `Chart.js` (biểu đồ realtime).
- Desktop: Java 24 (Swing GUI, Flat Look & Feel), Eclipse Paho MQTT Client (`org.eclipse.paho.client.mqttv3`).
- Scripts & Testing: Python 3.12 (Paho-MQTT test scripts), Batch Scripts (`.bat`).

**Spec:** Dựa trên các tài liệu trong workspace: `2.TH.docx` (Thực hành Mosquitto + Web Gauge), `2.MOS - Copy.pptx` (MQTT QoS, LWT, Retained, ACL), `13.wokwi - Copy.pptx` (Mô phỏng ESP32, DHT22, LED, Wokwi-GUEST), và hình vẽ bảng lớp (`mosq`, `wokwi`, `Client pub/sub`, `Java client`).

## Global Constraints
- Tất cả cấu hình kết nối MQTT (Host, Port TCP, Port WebSocket, Topics, Username, Password) phải được chuẩn hóa đồng nhất trên cả ESP32, Web và Java.
- Mạch ESP32 phải sử dụng chân GPIO 16 cho cảm biến DHT22 và GPIO 4 (kèm điện trở 220Ω) cho đèn LED đỏ ngoài + GPIO 2 cho LED tích hợp sẵn trên bo.
- Payload telemetry gửi lên topic `sensor/data` và `esp32/dht22/data` phải là chuỗi JSON chuẩn: `{"temperature": 28.6, "humidity": 62.4}`.
- Payload điều khiển gửi tới topic `esp32/led/command` là chuỗi văn bản `"ON"` hoặc `"OFF"`.
- Java Client không được yêu cầu cài đặt Maven/Gradle trên máy người dùng; phải có file `compile_and_run.bat` kèm thư viện `.jar` trong thư mục `lib/` để chạy được trực tiếp trên Java 24.
- Luôn cung cấp cấu hình dự phòng kết nối tới Public Broker (`broker.emqx.io`) để người dùng có thể chạy thử trực tiếp trên Wokwi Web ngay cả khi chưa khởi động Mosquitto trên máy cá nhân.

## Review Focus
1. **Lỗi WebSocket khi kết nối từ trình duyệt:** Trình duyệt web không thể kết nối tới cổng TCP thuần `1883`, bắt buộc Mosquitto phải cấu hình `listener 9001` kèm `protocol websockets`.
2. **Xác thực thất bại (Not Authorised):** Client bị Broker từ chối nếu sai Username/Password hoặc chưa cấp quyền khi `allow_anonymous false`.
3. **Mất kết nối mạng / ESP32 Offline:** Hệ thống phải sử dụng LWT (Last Will and Testament) trên topic `esp32/status` để thông báo cho Web và Java khi ESP32 bị ngắt kết nối đột ngột.
4. **Xử lý xung đột định dạng dữ liệu (JSON parse error):** Web và Java Client phải bọc `try-catch` khi parse payload, không để crash ứng dụng khi nhận bản tin lỗi.
5. **Cảnh báo ngưỡng nhiệt độ:** Khi nhiệt độ đo được vượt ngưỡng cài đặt (> 35°C), giao diện Java và Web phải lập tức đổi trạng thái cảnh báo trực quan.

---

## Danh Sách Các Task Triển Khai

### Task 1: Cấu Hình Mosquitto Broker & Bộ Thiết Lập Kết Nối Đa Môi Trường

**Files:**
- Create: `mosquitto/mosquitto.conf`
- Create: `mosquitto/password.txt`
- Create: `mosquitto/start_local_mosquitto.bat`
- Create: `shared/mqtt_config.json`
- Test: `scripts/test_broker_config.py`

**Interfaces:**
- Consumes: Thông số cổng `1883` (TCP), `9001` (WebSocket), credentials `user1` / `123456`.
- Produces: `shared/mqtt_config.json` cung cấp thông số tập trung cho ESP32, Web, Java và các script kiểm thử.

- [ ] **Step 1: Viết test kiểm tra tính hợp lệ của file cấu hình MQTT**
  Tạo `scripts/test_broker_config.py` đọc file `shared/mqtt_config.json` và kiểm tra các trường bắt buộc (`broker_host`, `tcp_port`, `ws_port`, `topics`, `auth`).

- [ ] **Step 2: Chạy test để xác nhận test báo lỗi do chưa có file**
  Chạy: `python scripts/test_broker_config.py`
  Kết quả mong đợi: FAIL (File not found).

- [ ] **Step 3: Tạo `shared/mqtt_config.json`**
  Định nghĩa đầy đủ:
  - `local`: host `localhost`, tcp `1883`, ws `9001`, auth `user1` / `123456`.
  - `public_fallback`: host `broker.emqx.io`, tcp `1883`, ws `8083` (hoặc wss `8084`), path `/mqtt`, anonymous `true`.
  - `topics`: `telemetry` ("esp32/dht22/data"), `legacy_telemetry` ("sensor/data"), `led_command` ("esp32/led/command"), `device_status` ("esp32/status").

- [ ] **Step 4: Tạo `mosquitto/mosquitto.conf` và `mosquitto/password.txt`**
  - Cấu hình TCP: `listener 1883 0.0.0.0`
  - Cấu hình WebSockets: `listener 9001 0.0.0.0`, `protocol websockets`
  - Cấu hình bảo mật: `allow_anonymous true` (hỗ trợ chuyển đổi nhanh sang `allow_anonymous false` và `password_file`).
  - Tạo script `mosquitto/start_local_mosquitto.bat` hỗ trợ chạy tự động khi có Mosquitto cài trên máy.

- [ ] **Step 5: Chạy lại test cấu hình để đảm bảo PASS**
  Chạy: `python scripts/test_broker_config.py`
  Kết quả mong đợi: PASS.

---

### Task 2: Mạch Mô Phỏng & Firmware ESP32 Trên Wokwi (DHT22 & Điều Khiển LED)

**Files:**
- Create: `wokwi/diagram.json`
- Create: `wokwi/libraries.txt`
- Create: `wokwi/sketch.ino`
- Test: `scripts/test_firmware_syntax.py`

**Interfaces:**
- Consumes: Cấu hình MQTT từ `shared/mqtt_config.json`.
- Produces: Firmware ESP32 đọc cảm biến DHT22 định kỳ 2s gửi JSON lên broker, subscribe topic lệnh để điều khiển chân GPIO LED.

- [ ] **Step 1: Viết script kiểm tra cú pháp và logic các hàm trong firmware ESP32**
  Tạo `scripts/test_firmware_syntax.py` kiểm tra các định nghĩa chân pin (DHT PIN 16, LED PIN 4), JSON format string, hàm `callback()`, và hàm `reconnect()`.

- [ ] **Step 2: Chạy test kiểm tra trước khi tạo code firmware**
  Chạy: `python scripts/test_firmware_syntax.py`
  Kết quả mong đợi: FAIL (File not found).

- [ ] **Step 3: Tạo `wokwi/diagram.json` và `wokwi/libraries.txt`**
  - `diagram.json`: ESP32 DevKit V1 nối DHT22 (VCC->3V3, GND->GND, SDA->GPIO16) và LED đỏ (Anode qua điện trở 220Ω vào GPIO4, Cathode vào GND).
  - `libraries.txt`: Liệt kê các thư viện `PubSubClient`, `DHT sensor library for ESPx`, `ArduinoJson`.

- [ ] **Step 4: Viết `wokwi/sketch.ino`**
  - Kết nối Wi-Fi `Wokwi-GUEST` (password rỗng).
  - Kết nối MQTT Broker kèm thiết lập LWT (`esp32/status` -> `"Offline"`, QoS 1, retain true). Khi kết nối thành công, publish `"Online"` lên topic này.
  - Vòng lặp `loop()` đọc nhiệt độ/độ ẩm từ DHT22 mỗi 2000ms, đóng gói JSON `{"temperature": xx.x, "humidity": yy.y}` và publish lên `esp32/dht22/data` và `sensor/data`.
  - Hàm `mqttCallback()` xử lý bản tin từ `esp32/led/command`: Nếu `"ON"` thì bật LED (HIGH); nếu `"OFF"` thì tắt LED (LOW). Đồng thời in thông báo rõ ràng ra Serial Monitor.

- [ ] **Step 5: Chạy lại test để xác nhận PASS**
  Chạy: `python scripts/test_firmware_syntax.py`
  Kết quả mong đợi: PASS.

---

### Task 3: Giao Diện Web Dashboard (Gauge Meter, Live Chart, LED Switch & MQTT WebSocket)

**Files:**
- Create: `web/index.html`
- Create: `web/styles.css`
- Create: `web/app.js`
- Test: `scripts/test_web_assets.py`

**Interfaces:**
- Consumes: WebSocket port `9001` (Local) hoặc `8083/8084` (Public), Topics `sensor/data`, `esp32/dht22/data`, `esp32/led/command`.
- Produces: Giao diện web hoàn chỉnh mở được trực tiếp trên trình duyệt, hiển thị 2 đồng hồ Gauge động và nút gạt điều khiển đèn LED.

- [ ] **Step 1: Viết script kiểm tra các file thành phần và cấu trúc DOM của Web Dashboard**
  Tạo `scripts/test_web_assets.py` kiểm tra sự tồn tại của `index.html`, `styles.css`, `app.js`, các id phần tử (`tempGauge`, `humGauge`, `ledToggle`, `connectionStatus`, `tempChart`, `logTable`).

- [ ] **Step 2: Chạy test để xác nhận FAIL**
  Chạy: `python scripts/test_web_assets.py`
  Kết quả mong đợi: FAIL.

- [ ] **Step 3: Xây dựng `web/index.html`**
  - Header với tiêu đề "IoT Smart Monitoring & Control System" và huy hiệu trạng thái kết nối (Badge: Connected / Disconnected).
  - Thanh chọn cấu hình Broker nhanh (Nút bấm chuyển đổi giữa Local Mosquitto `ws://localhost:9001` và Public EMQX `wss://broker.emqx.io:8084/mqtt`).
  - Khu vực hiển thị 2 đồng hồ Gauge (Canvas / SVG gauge) đo Nhiệt độ (°C) và Độ ẩm (%).
  - Khu vực công tắc điều khiển đèn LED (Toggle Switch) hiển thị trạng thái ON / OFF tức thì.
  - Biểu đồ đường biến thiên thời gian thực (Real-time Line Chart sử dụng Chart.js).
  - Bảng Log tin nhắn MQTT nhận được (gồm Cột Thời gian, Topic, Dữ liệu).

- [ ] **Step 4: Viết `web/styles.css`**
  - Thiết kế phong cách Dashboard hiện đại (Dark/Light card theme, typography rõ nét, hiệu ứng animation khi kim xoay gauge và nút gạt LED chuyển trạng thái).
  - Tương thích tốt trên cả màn hình máy tính và điện thoại.

- [ ] **Step 5: Viết `web/app.js`**
  - Tích hợp `mqtt.min.js`.
  - Hàm vẽ Canvas Gauge mượt mà không phụ thuộc thư viện nặng.
  - Xử lý sự kiện nhận tin nhắn từ `sensor/data` / `esp32/dht22/data`, trích xuất nhiệt độ và độ ẩm, cập nhật kim đồng hồ Gauge và điểm dữ liệu trên Chart.
  - Xử lý sự kiện click nút Toggle: publish chuỗi `"ON"` hoặc `"OFF"` lên topic `esp32/led/command`.
  - Cơ chế tự động thử kết nối lại (Auto Reconnect) và thông báo lỗi rõ ràng nếu chưa bật WebSockets trên Mosquitto.

- [ ] **Step 6: Chạy lại test tài nguyên Web để xác nhận PASS**
  Chạy: `python scripts/test_web_assets.py`
  Kết quả mong đợi: PASS.

---

### Task 4: Ứng Dụng Java Swing Desktop Client (GUI Dashboard & MQTT Client)

**Files:**
- Create: `java-client/src/com/iot/MqttManager.java`
- Create: `java-client/src/com/iot/MainFrame.java`
- Create: `java-client/src/com/iot/App.java`
- Download: `java-client/lib/org.eclipse.paho.client.mqttv3-1.2.5.jar`
- Create: `java-client/compile_and_run.bat`
- Test: `scripts/test_java_compilation.py`

**Interfaces:**
- Consumes: TCP port `1883`, thư viện Paho MQTT Client JAR, Topics dữ liệu và điều khiển.
- Produces: Ứng dụng Desktop Java Swing chạy độc lập, hiển thị Card số đo, cảnh báo nhiệt độ cao (> 35°C), nút bật/tắt LED, bảng log và tính năng xuất log ra file CSV.

- [ ] **Step 1: Viết script tự động tải thư viện Paho MQTT JAR và biên dịch thử Java**
  Tạo `scripts/test_java_compilation.py` kiểm tra file JAR trong `java-client/lib/` và chạy lệnh `javac` để biên dịch mã nguồn Java.

- [ ] **Step 2: Chạy test để xác nhận FAIL trước khi tạo code Java**
  Chạy: `python scripts/test_java_compilation.py`
  Kết quả mong đợi: FAIL (Source files / JAR not present).

- [ ] **Step 3: Tải thư viện Eclipse Paho MQTT Client JAR vào `java-client/lib/`**
  Tải `org.eclipse.paho.client.mqttv3-1.2.5.jar` từ Maven Central repository và lưu vào `java-client/lib/`.

- [ ] **Step 4: Xây dựng `java-client/src/com/iot/MqttManager.java`**
  - Đóng gói lớp `MqttClient` từ Paho.
  - Phương thức `connect(String brokerUrl, String clientId, String username, String password)`.
  - Phương thức `subscribe(String topic, int qos)`.
  - Phương thức `publish(String topic, String message, int qos, boolean retained)`.
  - Interface lắng nghe sự kiện: `onMessageReceived(String topic, String payload)`, `onStatusChanged(boolean connected, String status)`.

- [ ] **Step 5: Xây dựng giao diện `java-client/src/com/iot/MainFrame.java` & `App.java`**
  - Giao diện cửa sổ Swing:
    - Bảng thông số: 2 Thẻ Card lớn đo Nhiệt độ và Độ ẩm. Khi Nhiệt độ > 35°C, Card tự động chuyển màu cảnh báo đỏ và hiển thị icon cảnh báo.
    - Cụm điều khiển: Nút "BẬT ĐÈN (ON)" và "TẮT ĐÈN (OFF)" gửi lệnh MQTT xuống ESP32.
    - Khu vực cấu hình kết nối: Chọn kết nối Local Mosquitto hoặc Public EMQX.
    - Bảng lịch sử nhận tin (JTable với các cột: Thời Gian, Topic, Dữ Liệu Nhận).
    - Nút chức năng "Xuất Báo Cáo CSV (Export CSV)" lưu toàn bộ dữ liệu đo ra file `.csv` phục vụ nộp bài tập.

- [ ] **Step 6: Tạo `java-client/compile_and_run.bat`**
  - Script tự động chạy `javac -encoding UTF-8 -cp "lib/*" src/com/iot/*.java` và `java -cp "src;lib/*" com.iot.App`.

- [ ] **Step 7: Chạy kiểm tra biên dịch bằng `scripts/test_java_compilation.py`**
  Chạy: `python scripts/test_java_compilation.py`
  Kết quả mong đợi: PASS (Compiles cleanly with 0 errors).

---

### Task 5: Kiểm Thử Tích Hợp Toàn Diện & Tài Liệu Hướng Dẫn Sử Dụng (README)

**Files:**
- Create: `scripts/mock_esp32_publisher.py` (Mô phỏng gửi dữ liệu DHT22 để test Web & Java mà không cần mở Wokwi)
- Create: `scripts/mock_led_subscriber.py` (Mô phỏng phản hồi trạng thái LED khi nhận lệnh từ Web & Java)
- Create: `README.md` (Tài liệu hướng dẫn chi tiết từ A-Z)
- Test: `scripts/test_end_to_end.py`

**Interfaces:**
- Consumes: Tất cả các thành phần đã hoàn thành ở Task 1, 2, 3, 4.
- Produces: Hệ thống hoạt động trơn tru 100%, có kịch bản test tự động và tài liệu minh họa đầy đủ.

- [ ] **Step 1: Viết kịch bản giả lập `scripts/mock_esp32_publisher.py`**
  Kịch bản gửi liên tục dữ liệu nhiệt độ/độ ẩm biến thiên sau mỗi 2 giây lên broker, hỗ trợ test Web Dashboard và Java App ngay lập tức.

- [ ] **Step 2: Viết kịch bản giả lập `scripts/mock_led_subscriber.py`**
  Lắng nghe topic `esp32/led/command`, in thông báo trạng thái đèn bật/tắt khi người dùng bấm nút trên Web hoặc Java.

- [ ] **Step 3: Viết test kiểm tra luồng thông điệp 2 chiều `scripts/test_end_to_end.py`**
  Kiểm tra: Publish telemetry -> Subscriber nhận đúng; Publish lệnh LED -> Broker nhận và forward thành công.

- [ ] **Step 4: Chạy test End-to-End**
  Chạy: `python scripts/test_end_to_end.py`
  Kết quả mong đợi: PASS.

- [ ] **Step 5: Soạn thảo `README.md` hoàn chỉnh**
  Hướng dẫn rõ ràng từng bước:
  1. Hướng dẫn chạy Mosquitto Broker trên máy tính (kèm giải pháp chạy không cần cài qua Public Broker).
  2. Hướng dẫn mở và chạy mạch mô phỏng trên Wokwi (cả trên Web Wokwi.com và Wokwi VS Code).
  3. Hướng dẫn mở Web Dashboard trên trình duyệt xem đồng hồ Gauge xoay và biểu đồ.
  4. Hướng dẫn khởi chạy ứng dụng Java Swing Client bằng 1 click (`compile_and_run.bat`).
  5. Checklist đối chiếu yêu cầu môn học và hình vẽ bảng lớp.
