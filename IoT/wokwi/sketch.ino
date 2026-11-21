/*
 * =====================================================================================
 * DỰ ÁN: HỆ THỐNG GIÁM SÁT IoT & ĐIỀU KHIỂN THIẾT BỊ QUA GIAO THỨC MQTT
 * MÃ NGUỒN C++ CHO VI ĐIỀU KHIỂN ESP32 (MÔ PHỎNG WOKWI & MẠCH THẬT)
 * =====================================================================================
 * - Phần cứng kết nối:
 *   + Cảm biến DHT22: Đo nhiệt độ và độ ẩm môi trường (GPIO 16)
 *   + Đèn LED Đỏ ngoài: Đèn báo động quá nhiệt / điều khiển từ xa (GPIO 4 qua trở 220 Ohm)
 *   + Đèn LED Xanh bo mạch: Đèn tích hợp trên bo ESP32 (GPIO 2, sáng đồng bộ LED đỏ)
 *   + Công tắc gạt (Switch): Gạt chọn chế độ AUTO / MANUAL (GPIO 14)
 * =====================================================================================
 */

// BƯỚC 1: KHAI BÁO CÁC THƯ VIỆN CẦN THIẾT
#include <WiFi.h>          // Thư viện kết nối Wi-Fi có sẵn của chip ESP32
#include <PubSubClient.h>  // Thư viện giao tiếp giao thức MQTT Client (bản 2.8)
#include <DHTesp.h>        // Thư viện đọc tín hiệu cảm biến nhiệt độ & độ ẩm DHT22
#include <ArduinoJson.h>   // Thư viện đóng gói và xử lý chuỗi định dạng JSON (bản 6.21)

// BƯỚC 2: ĐỊNH NGHĨA CÁC CHÂN PHẦN CỨNG (HARDWARE PIN DEFINITIONS)
#define DHTPIN 16          // Chân số 16 (GPIO 16): Nối với chân tín hiệu SDA của cảm biến DHT22
#define LEDPIN 4           // Chân số 4 (GPIO 4): Nối với cực dương (Anode) đèn LED đỏ ngoài qua điện trở 220 Ohm
#define LED_BUILTIN_PIN 2  // Chân số 2 (GPIO 2): Đèn LED xanh tích hợp sẵn của bo mạch ESP32
#define MODE_SWITCH_PIN 14 // Chân số 14 (GPIO 14): Nối với công tắc gạt (Trái = AUTO tự nhảy số | Phải = MANUAL kéo tay)

// BƯỚC 3: CẤU HÌNH THÔNG SỐ WI-FI WOKWI
const char* ssid = "Wokwi-GUEST"; // Tên điểm truy cập Wi-Fi ảo của hệ thống mô phỏng Wokwi
const char* password = "";        // Mật khẩu Wi-Fi (Wokwi-GUEST không dùng mật khẩu)

// BƯỚC 4: CẤU HÌNH ĐỊA CHỈ MQTT BROKER
// - Mặc định trỏ tới IP của máy bạn hoặc broker.emqx.io để chạy qua Internet
const char* mqtt_server = "192.168.137.240"; // Địa chỉ IP máy tính đang chạy Java Broker (hoặc broker.emqx.io)
const int mqtt_port = 1883;                  // Cổng TCP tiêu chuẩn của MQTT Broker (mặc định là 1883)
const char* mqtt_user = "user1";             // Tên tài khoản đăng nhập MQTT Broker
const char* mqtt_pass = "123";               // Mật khẩu đăng nhập MQTT Broker

// BƯỚC 5: KHAI BÁO CÁC CHỦ ĐỀ MQTT (TOPICS) DÙNG CHUNG TOÀN HỆ THỐNG
const char* TOPIC_TELEMETRY = "esp32/sensor/dht22/data"; // Kênh chuẩn: ESP32 bắn dữ liệu cảm biến (JSON)
const char* TOPIC_LEGACY_TELEMETRY = "sensor/data";      // Kênh tương thích: Bắn song song theo đề bài cũ
const char* TOPIC_LED_CMD = "esp32/led/command";         // Kênh nhận lệnh: Client gửi "ON" hoặc "OFF" điều khiển đèn
const char* TOPIC_STATUS = "esp32/status";               // Kênh báo tử/sống: Báo trạng thái "Online" hoặc "Offline" (LWT)

// BƯỚC 6: KHỞI TẠO CÁC ĐỐI TƯỢNG ĐIỀU KHIỂN
WiFiClient espClient;           // Đối tượng quản lý kết nối Socket TCP cấp thấp qua Wi-Fi
PubSubClient client(espClient); // Đối tượng MQTT Client bọc ngoài Socket TCP để gửi/nhận gói tin MQTT
DHTesp dht;                     // Đối tượng giao tiếp với cảm biến DHT22

// BƯỚC 7: CÁC BIẾN QUẢN LÝ THỜI GIAN VÀ TRẠNG THÁI HỆ THỐNG
unsigned long lastMsgTime = 0;          // Lưu mốc thời gian lần gửi cảm biến gần nhất (dùng đếm millis)
const unsigned long MSG_INTERVAL = 2000;// Chu kỳ gửi dữ liệu: Cứ mỗi 2000 ms (2 giây) gửi 1 lần
const float TEMP_ALERT_THRESHOLD = 35.0;// Ngưỡng nhiệt độ báo động: Vượt quá 35.0 độ C sẽ tự động bật đèn đỏ
bool highTempAutoLed = false;           // Cờ ghi nhớ đèn đã được tự động bật do quá nhiệt hay chưa (chống gửi lặp)
bool lastManualMode = false;            // Cờ lưu trạng thái công tắc gạt trước đó để in log khi gạt đổi chế độ

/**
 * =====================================================================================
 * HÀM 1: mqttCallback() - HÀM LẮNG NGHE LỆNH TỪ BROKER (SUBSCRIBER CALLBACK)
 * =====================================================================================
 * - Được thư viện PubSubClient tự động kích hoạt mỗi khi có gói tin từ Broker đẩy về.
 * - Tham số:
 *   + topic: Tên kênh vừa nhận được tin (vd: esp32/led/command)
 *   + payload: Mảng byte chứa nội dung tin nhắn (vd: chữ "ON" hoặc "OFF")
 *   + length: Độ dài của mảng byte
 * =====================================================================================
 */
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  // Chuyển mảng byte payload thành chuỗi ký tự String dễ xử lý
  String message = "";
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  message.trim(); // Cắt bỏ các ký tự xuống dòng hoặc khoảng trắng thừa ở 2 đầu

  // In thông tin ra cửa sổ Serial Monitor để quan sát khi debug
  Serial.print("MQTT: ");
  Serial.print(topic);
  Serial.print(" -> ");
  Serial.println(message);

  // KIỂM TRA: Nếu tin nhắn đến đúng kênh điều khiển đèn LED (esp32/led/command)
  if (String(topic) == TOPIC_LED_CMD) {
    // Nếu lệnh là "ON" hoặc "1" -> BẬT ĐÈN LED
    if (message.equalsIgnoreCase("ON") || message == "1") {
      digitalWrite(LEDPIN, HIGH);          // Xuất mức điện áp cao (3.3V) ra chân GPIO 4 -> Đèn LED đỏ ngoài SÁNG
      digitalWrite(LED_BUILTIN_PIN, HIGH); // Bật luôn đèn LED xanh tích hợp trên bo ESP32 (GPIO 2)
      Serial.println("[ESP32] >>> ĐÈN LED: ĐÃ BẬT (HIGH) <<<");
    } 
    // Nếu lệnh là "OFF" hoặc "0" -> TẮT ĐÈN LED
    else if (message.equalsIgnoreCase("OFF") || message == "0") {
      digitalWrite(LEDPIN, LOW);          // Xuất mức điện áp thấp (0V) ra chân GPIO 4 -> Đèn LED đỏ ngoài TẮT
      digitalWrite(LED_BUILTIN_PIN, LOW); // Tắt đèn LED xanh tích hợp trên bo ESP32 (GPIO 2)
      Serial.println("[ESP32] >>> ĐÈN LED: ĐÃ TẮT (LOW) <<<");
    }
  }
}

/**
 * =====================================================================================
 * HÀM 2: reconnectMQTT() - TỰ ĐỘNG KẾT NỐI LẠI MQTT BROKER KÈM DI CHÚC (LWT)
 * =====================================================================================
 * - Chạy trong vòng lặp while chừng nào ESP32 chưa kết nối được tới Broker.
 * - Tự sinh ClientID ngẫu nhiên để không bị trùng với các máy khác trong nhóm.
 * - Cài đặt gói tin LWT (Last Will and Testament): Nếu đứt mạng, Broker tự phát "Offline".
 * =====================================================================================
 */
void reconnectMQTT() {
  // Lặp lại việc kết nối chừng nào chưa thành công
  while (!client.connected()) {
    Serial.print("Đang kết nối MQTT Broker (");
    Serial.print(mqtt_server);
    Serial.print(")... ");

    // Sinh mã Client ID ngẫu nhiên: ESP32_Wokwi_Client_XXXX (tránh bị đá kết nối)
    String clientId = "ESP32_Wokwi_Client_" + String(random(0xffff), HEX);

    // CẤU HÌNH LWT: Cài di chúc trước cho Broker
    // - Topic di chúc: esp32/status
    // - Nội dung: "Offline"
    // - QoS: 1
    // - Retain: true (Broker sẽ lưu giữ chữ "Offline" cho bất kỳ ai hỏi sau đó)
    bool connected = false;
    if (strlen(mqtt_user) > 0 && String(mqtt_server) != "broker.emqx.io") {
      // Kết nối có xác thực tài khoản/mật khẩu (user1/123)
      connected = client.connect(clientId.c_str(), mqtt_user, mqtt_pass, TOPIC_STATUS, 1, true, "Offline");
    } else {
      // Kết nối ẩn danh (dành cho Public EMQX)
      connected = client.connect(clientId.c_str(), TOPIC_STATUS, 1, true, "Offline");
    }

    // NẾU KẾT NỐI THÀNH CÔNG:
    if (connected) {
      Serial.println("THÀNH CÔNG!");

      // 1. Bắn thông báo trạng thái "Online" lên topic esp32/status (kèm retain=true)
      client.publish(TOPIC_STATUS, "Online", true);

      // 2. Đăng ký (Subscribe) kênh esp32/led/command để đón nhận lệnh bật/tắt đèn từ Client
      client.subscribe(TOPIC_LED_CMD);
      Serial.print("Đã subscribe topic điều khiển: ");
      Serial.println(TOPIC_LED_CMD);
    } 
    // NẾU KẾT NỐI THẤT BẠI:
    else {
      Serial.print("Thất bại, mã lỗi rc=");
      Serial.print(client.state());
      Serial.println(" -> Thử lại sau 5 giây...");
      delay(5000); // Chờ 5 giây rồi mới lặp lại thử kết nối
    }
  }
}

/**
 * =====================================================================================
 * HÀM 3: setup() - KHỞI TẠO HỆ THỐNG (CHẠY 1 LẦN DUY NHẤT KHI BẬT NGUỒN)
 * =====================================================================================
 * - Khởi tạo cổng truyền thông nối tiếp Serial (115200 baud).
 * - Cấu hình chức năng các chân GPIO (OUTPUT cho LED, INPUT_PULLUP cho công tắc).
 * - Bật cảm biến DHT22, bắt Wi-Fi Wokwi và thiết lập tham số MQTT Client.
 * =====================================================================================
 */
void setup() {
  Serial.begin(115200); // Mở cổng giao tiếp Serial với máy tính ở tốc độ 115200 bps
  delay(100);

  Serial.println();
  Serial.println("==================================================");
  Serial.println(" KHỞI ĐỘNG HỆ THỐNG GIÁM SÁT ESP32 TRÊN WOKWI");
  Serial.println("==================================================");

  // 1. Cấu hình chân GPIO điều khiển đèn LED làm ngõ ra (OUTPUT)
  pinMode(LEDPIN, OUTPUT);          // Chân 4 điều khiển đèn LED đỏ ngoài
  pinMode(LED_BUILTIN_PIN, OUTPUT);  // Chân 2 điều khiển đèn LED xanh trên bo

  // 2. Cấu hình chân GPIO công tắc gạt làm ngõ vào kéo lên (INPUT_PULLUP)
  // Nhờ điện trở kéo lên nguồn 3.3V bên trong chip, khi hở mạch là HIGH, khi đóng GND là LOW
  pinMode(MODE_SWITCH_PIN, INPUT_PULLUP);
  lastManualMode = (digitalRead(MODE_SWITCH_PIN) == HIGH); // Đọc trạng thái ban đầu của công tắc
  randomSeed(esp_random());                               // Khởi tạo mầm sinh số ngẫu nhiên phần cứng

  // Ban đầu tắt cả 2 đèn LED
  digitalWrite(LEDPIN, LOW);
  digitalWrite(LED_BUILTIN_PIN, LOW);

  // 3. Khởi tạo cảm biến DHT22 trên chân GPIO 16
  dht.setup(DHTPIN, DHTesp::DHT22);
  Serial.println("[SETUP] Cảm biến DHT22 sẵn sàng (GPIO 16) | Switch AUTO/MANUAL (GPIO 14).");

  // 4. Kết nối mạng Wi-Fi ảo của Wokwi
  Serial.print("[WIFI] Đang kết nối tới ");
  Serial.println(ssid);
  WiFi.begin(ssid, password, 6);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print("."); // In dấu chấm chờ kết nối Wi-Fi
  }
  Serial.println();
  Serial.print("[WIFI] Đã kết nối! IP được cấp: ");
  Serial.println(WiFi.localIP());

  // 5. Cấu hình MQTT Client: Địa chỉ server, cổng và hàm callback xử lý tin nhắn đến
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(mqttCallback);
}

/**
 * =====================================================================================
 * HÀM 4: loop() - VÒNG LẶP CHÍNH CỦA HỆ THỐNG (CHẠY LIÊN TỤC VÔ HẠN)
 * =====================================================================================
 * - Kiểm tra kết nối MQTT và lắng nghe gói tin đến qua client.loop().
 * - Đọc công tắc chế độ AUTO / MANUAL.
 * - Định kỳ mỗi 2 giây đọc cảm biến DHT22, đóng gói JSON và gửi PUBLISH lên Broker.
 * - Tự động bảo vệ tại biên: Nhiệt độ > 35°C tự động bật đèn LED đỏ ngay lập tức.
 * =====================================================================================
 */
void loop() {
  // 1. Kiểm tra kết nối MQTT: Nếu rớt mạng thì lập tức kết nối lại
  if (!client.connected()) {
    reconnectMQTT();
  }
  // 2. Gọi hàm client.loop() để duy trì keepalive và nhận gói tin điều khiển từ Broker
  client.loop();

  // 3. Đọc công tắc gạt chân 14: HIGH = Gạt sang phải (MANUAL) | LOW = Gạt sang trái (AUTO)
  bool isManualMode = (digitalRead(MODE_SWITCH_PIN) == HIGH);
  if (isManualMode != lastManualMode) {
    lastManualMode = isManualMode;
    Serial.println(isManualMode
      ? "[MODE] KÉO TAY (MANUAL) -> Đọc trực tiếp giá trị bạn kéo trên DHT22"
      : "[MODE] TỰ ĐỘNG (AUTO) -> Tự thu cảm biến kèm dao động mỗi 2 giây");
  }

  // 4. Kiểm tra chu kỳ gửi dữ liệu: Dùng bộ đếm millis() không chặn (Non-blocking)
  unsigned long now = millis();
  if (now - lastMsgTime > MSG_INTERVAL) {
    lastMsgTime = now; // Cập nhật mốc thời gian gửi vừa xong

    // 5. Đọc nhiệt độ và độ ẩm thực tế từ cảm biến DHT22
    TempAndHumidity data = dht.getTempAndHumidity();

    // Kiểm tra cảm biến có bị lỗi đọc hay không (isnan = is not a number)
    if (isnan(data.temperature) || isnan(data.humidity)) {
      Serial.println("[CẢNH BÁO] Lỗi đọc dữ liệu từ cảm biến DHT22!");
      return; // Bỏ qua lượt gửi này nếu cảm biến lỗi
    }

    float outTemp = data.temperature;
    float outHum = data.humidity;

    // 6. Xử lý chế độ AUTO: Thêm dao động ngẫu nhiên nhỏ để số trên màn hình tự nhảy
    if (!isManualMode) {
      // Cộng trừ ngẫu nhiên từ -1.5 đến +2.5 độ C cho nhiệt độ
      outTemp = constrain(outTemp + random(-15, 26) / 10.0, -40.0, 80.0);
      // Cộng trừ ngẫu nhiên từ -3.0 đến +3.0 % cho độ ẩm
      outHum = constrain(outHum + random(-30, 31) / 10.0, 0.0, 100.0);
    }
    // Làm tròn lấy 1 chữ số thập phân sau dấu phẩy (vd: 32.5)
    outTemp = round(outTemp * 10.0) / 10.0;
    outHum = round(outHum * 10.0) / 10.0;

    // 7. Đóng gói dữ liệu thành chuỗi JSON: {"temperature": 32.5, "humidity": 65.0}
    StaticJsonDocument<128> doc;
    doc["temperature"] = outTemp;
    doc["humidity"] = outHum;

    char jsonBuffer[128];
    serializeJson(doc, jsonBuffer); // Chuyển đổi đối tượng JSON sang mảng ký tự chuỗi

    // 8. Bắn gói tin PUBLISH sang MQTT Broker
    client.publish(TOPIC_TELEMETRY, jsonBuffer);        // Gửi vào topic chuẩn: esp32/sensor/dht22/data
    client.publish(TOPIC_LEGACY_TELEMETRY, jsonBuffer); // Gửi song song vào topic tương thích: sensor/data

    // 9. LOGIC TỰ ĐỘNG BẢO VỆ TẠI BIÊN (EDGE COMPUTING)
    // Nếu nhiệt độ vượt quá ngưỡng cảnh báo (35.0 độ C) và cờ chưa bật
    if (outTemp > TEMP_ALERT_THRESHOLD && !highTempAutoLed) {
      highTempAutoLed = true;               // Bật cờ ghi nhớ
      digitalWrite(LEDPIN, HIGH);          // Tự kích hoạt bật đèn LED đỏ ngay lập tức
      digitalWrite(LED_BUILTIN_PIN, HIGH); // Bật LED xanh bo mạch
      client.publish(TOPIC_LED_CMD, "ON");  // Bắn tin báo "ON" lên topic esp32/led/command để Client đồng bộ
      Serial.println("[ESP32] [CẢNH BÁO] Nhiệt độ > 35°C -> Tự động BẬT đèn LED!");
    } 
    // Nếu nhiệt độ hạ xuống bằng hoặc dưới ngưỡng 35.0 độ C và đèn đang bật tự động
    else if (outTemp <= TEMP_ALERT_THRESHOLD && highTempAutoLed) {
      highTempAutoLed = false;              // Tắt cờ ghi nhớ
      digitalWrite(LEDPIN, LOW);           // Tự tắt đèn LED đỏ
      digitalWrite(LED_BUILTIN_PIN, LOW);  // Tắt LED xanh bo mạch
      client.publish(TOPIC_LED_CMD, "OFF"); // Bắn tin báo "OFF" lên topic esp32/led/command
      Serial.println("[ESP32] Nhiệt độ hạ ngưỡng -> Tự động TẮT đèn LED!");
    }

    // 10. In chuỗi dữ liệu đã gửi ra màn hình Serial Monitor
    Serial.print(isManualMode ? "[MANUAL] MQTT -> " : "[AUTO] MQTT -> ");
    Serial.print(TOPIC_TELEMETRY);
    Serial.print(": ");
    Serial.println(jsonBuffer);
  }
}
