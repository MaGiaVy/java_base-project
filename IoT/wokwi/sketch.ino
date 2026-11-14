/*
 * =========================================================================
 * Dự án: Hệ Thống Giám Sát IoT & Điều Khiển Thiết Bị Qua MQTT
 * Mạch mô phỏng ESP32 trên Wokwi (DHT22 + LED đỏ + Broker Mosquitto/EMQX)
 * =========================================================================
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <DHTesp.h>
#include <ArduinoJson.h>

// --- Cấu hình Chân Phần Cứng (Hardware Pins) ---
#define DHTPIN 16         // Chân dữ liệu cảm biến DHT22
#define LEDPIN 4          // Chân điều khiển đèn LED đỏ ngoài (qua trở 220 Ohm)
#define LED_BUILTIN_PIN 2 // Đèn LED xanh tích hợp sẵn trên bo ESP32

// --- Cấu hình Mạng Wi-Fi Wokwi ---
const char* ssid = "Wokwi-GUEST";
const char* password = "";

// --- Cấu hình MQTT Broker ---
// Mặc định dùng broker.emqx.io để chạy ngay trên Wokwi Web mà không cần cài broker
// Nếu chạy Mosquitto trên máy tính qua Wokwi for VS Code / Private Gateway:
// Đổi mqtt_server thành địa chỉ IPv4 máy tính (vd: "192.168.1.10" hoặc "10.0.2.2")
const char* mqtt_server = "broker.emqx.io";
const int mqtt_port = 1883;
const char* mqtt_user = "user1";
const char* mqtt_pass = "123";

// --- Danh mục Topic MQTT (Đồng bộ với Web Dashboard & Java Client) ---
const char* TOPIC_TELEMETRY = "esp32/sensor/dht22/data";
const char* TOPIC_LEGACY_TELEMETRY = "sensor/data";
const char* TOPIC_LED_CMD = "esp32/led/command";
const char* TOPIC_STATUS = "esp32/status";

// Khởi tạo các đối tượng điều khiển
WiFiClient espClient;
PubSubClient client(espClient);
DHTesp dht;

unsigned long lastMsgTime = 0;
const unsigned long MSG_INTERVAL = 2000; // Đọc & gửi cảm biến mỗi 2 giây

// --- Hàm xử lý khi nhận lệnh từ Broker (Subscriber) ---
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message = "";
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  message.trim();

  Serial.print("MQTT: ");
  Serial.print(topic);
  Serial.print(" -> ");
  Serial.println(message);

  if (String(topic) == TOPIC_LED_CMD) {
    if (message.equalsIgnoreCase("ON") || message == "1") {
      digitalWrite(LEDPIN, HIGH);
      digitalWrite(LED_BUILTIN_PIN, HIGH);
      Serial.println("[ESP32] >>> ĐÈN LED: ĐÃ BẬT (HIGH) <<<");
    } else if (message.equalsIgnoreCase("OFF") || message == "0") {
      digitalWrite(LEDPIN, LOW);
      digitalWrite(LED_BUILTIN_PIN, LOW);
      Serial.println("[ESP32] >>> ĐÈN LED: ĐÃ TẮT (LOW) <<<");
    }
  }
}

// --- Hàm kết nối lại MQTT Broker kèm Last Will (LWT) ---
void reconnectMQTT() {
  while (!client.connected()) {
    Serial.print("Đang kết nối MQTT Broker (");
    Serial.print(mqtt_server);
    Serial.print(")... ");

    String clientId = "ESP32_Wokwi_Client_" + String(random(0xffff), HEX);

    // Cấu hình LWT: Khi ESP32 mất kết nối bất ngờ, Broker sẽ tự phát "Offline"
    bool connected = false;
    if (strlen(mqtt_user) > 0 && String(mqtt_server) != "broker.emqx.io") {
      connected = client.connect(clientId.c_str(), mqtt_user, mqtt_pass, TOPIC_STATUS, 1, true, "Offline");
    } else {
      connected = client.connect(clientId.c_str(), TOPIC_STATUS, 1, true, "Offline");
    }

    if (connected) {
      Serial.println("THÀNH CÔNG!");
      // Báo trạng thái Online cho Web và Java Client
      client.publish(TOPIC_STATUS, "Online", true);
      // Đăng ký nhận lệnh điều khiển đèn LED
      client.subscribe(TOPIC_LED_CMD);
      Serial.print("Đã subscribe topic điều khiển: ");
      Serial.println(TOPIC_LED_CMD);
    } else {
      Serial.print("Thất bại, mã lỗi rc=");
      Serial.print(client.state());
      Serial.println(" -> Thử lại sau 5 giây...");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(100);

  Serial.println();
  Serial.println("==================================================");
  Serial.println(" KHỞI ĐỘNG HỆ THỐNG GIÁM SÁT ESP32 TRÊN WOKWI");
  Serial.println("==================================================");

  // Cấu hình chân GPIO LED làm ngõ ra
  pinMode(LEDPIN, OUTPUT);
  pinMode(LED_BUILTIN_PIN, OUTPUT);
  digitalWrite(LEDPIN, LOW);
  digitalWrite(LED_BUILTIN_PIN, LOW);

  // Khởi tạo cảm biến DHT22
  dht.setup(DHTPIN, DHTesp::DHT22);
  Serial.println("[SETUP] Cảm biến DHT22 đã sẵn sàng tại chân GPIO 16.");

  // Kết nối Wi-Fi
  Serial.print("[WIFI] Đang kết nối tới ");
  Serial.println(ssid);
  WiFi.begin(ssid, password, 6);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("[WIFI] Đã kết nối! IP được cấp: ");
  Serial.println(WiFi.localIP());

  // Cấu hình MQTT Client
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(mqttCallback);
}

void loop() {
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();

  unsigned long now = millis();
  if (now - lastMsgTime > MSG_INTERVAL) {
    lastMsgTime = now;

    // Đọc thông số nhiệt độ và độ ẩm từ DHT22
    TempAndHumidity data = dht.getTempAndHumidity();

    if (isnan(data.temperature) || isnan(data.humidity)) {
      Serial.println("[CẢNH BÁO] Lỗi đọc dữ liệu từ cảm biến DHT22!");
      return;
    }

    // Đóng gói JSON payload an toàn (tránh dangling pointer của temporary String)
    StaticJsonDocument<128> doc;
    doc["temperature"] = round(data.temperature * 10.0) / 10.0;
    doc["humidity"] = round(data.humidity * 10.0) / 10.0;

    char jsonBuffer[128];
    serializeJson(doc, jsonBuffer);

    // Gửi lên Broker (Publish)
    client.publish(TOPIC_TELEMETRY, jsonBuffer);
    client.publish(TOPIC_LEGACY_TELEMETRY, jsonBuffer);

    // In thông điệp ra Serial Monitor đúng chuẩn demo
    Serial.print("MQTT -> ");
    Serial.print(TOPIC_TELEMETRY);
    Serial.print(": ");
    Serial.println(jsonBuffer);
  }
}
