package com.iot;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * =========================================================================
 * FILE: MqttManager.java - QUẢN LÝ TẦNG MẠNG & GIAO THỨC MQTT CHO CLIENT
 * =========================================================================
 * - Nhiệm vụ: Đóng gói toàn bộ thư viện Eclipse Paho MQTT v3 Client.
 * - Vai trò:
 *   + Thực hiện kết nối/ngắt kết nối tới MQTT Broker (Local hoặc Public EMQX).
 *   + Tự động dò địa chỉ IP phụ 127.0.0.2:1883 nếu máy tính bị kẹt port Mosquitto.
 *   + Cung cấp các hàm mạng: subscribe() (đăng ký topic), publish() (bắn tin đi).
 *   + Nhận gói tin từ mạng và bắn callback về MainFrame qua interface MqttListener.
 * =========================================================================
 */
public class MqttManager {

    /**
     * Interface MqttListener: Cầu nối truyền tin giữa tầng mạng (MqttManager) và tầng giao diện (MainFrame).
     * Bất kỳ khi nào mạng có sự kiện (kết nối xong, mất mạng, có tin nhắn mới), hàm tương ứng sẽ được gọi.
     */
    public interface MqttListener {
        // Gọi khi kết nối thành công tới Broker
        void onConnected(String brokerUrl);

        // Gọi khi bị mất kết nối đột ngột (rớt mạng, tắt broker)
        void onConnectionLost(String reason);

        // Gọi khi nhận được 1 bản tin MQTT mới từ Broker gửi về
        void onMessage(String topic, String payload);
    }

    // Đối tượng Client của thư viện Eclipse Paho đảm nhận việc giao tiếp Socket TCP chuẩn MQTT
    private MqttClient client;

    // Đối tượng lắng nghe callback để báo về cho MainFrame cập nhật giao diện
    private MqttListener listener;

    /**
     * Hàm khởi tạo (Constructor): Nhận listener từ MainFrame truyền vào
     * @param listener: Đối tượng cài đặt interface MqttListener (chính là MainFrame)
     */
    public MqttManager(MqttListener listener) {
        this.listener = listener;
    }

    /**
     * Hàm connect(): Thực hiện kết nối TCP tới MQTT Broker
     * @param brokerUrl: Địa chỉ Broker (vd: tcp://broker.emqx.io:1883 hoặc tcp://localhost:1883)
     * @param clientId: Mã định danh duy nhất của Client (tránh bị trùng làm đá nhau ra)
     * @param username: Tên đăng nhập (user1) - nếu broker yêu cầu xác thực
     * @param password: Mật khẩu (123) - nếu broker yêu cầu xác thực
     * @throws MqttException: Ném lỗi nếu sai địa chỉ hoặc không nối được mạng
     */
    public synchronized void connect(String brokerUrl, String clientId, String username, String password) throws MqttException {
        // BƯỚC 1: Nếu client cũ đang kết nối thì đóng kết nối sạch sẽ trước khi tạo kết nối mới
        if (client != null && client.isConnected()) {
            client.disconnect(); // Gửi gói DISCONNECT lên Broker
            client.close();      // Giải phóng Socket và bộ nhớ RAM
        }

        // BƯỚC 2: Cơ chế dò cổng thông minh (Auto-probing) cho môi trường Windows
        // Khi người dùng nhập "localhost:1883" hoặc "127.0.0.1:1883", có thể cổng 1883 trên 127.0.0.1
        // đang bị dịch vụ Mosquitto của Windows chiếm. Khi đó Java Broker tự chuyển sang 127.0.0.2.
        String effectiveUrl = brokerUrl;
        if ("tcp://localhost:1883".equalsIgnoreCase(brokerUrl)
                || "tcp://127.0.0.1:1883".equalsIgnoreCase(brokerUrl)) {
            // Thử mở 1 socket thăm dò 127.0.0.2:1883 trong vòng 150 mili-giây
            try (java.net.Socket probe = new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.2", 1883), 150);
                // Nếu thăm dò thành công (có Java Broker đang chờ ở đó) -> Tự động chuyển URL sang 127.0.0.2
                effectiveUrl = "tcp://127.0.0.2:1883";
            } catch (Exception ignored) {
                // Nếu không thấy 127.0.0.2 thì giữ nguyên URL gốc để thử 127.0.0.1
            }
        }

        // BƯỚC 3: Khởi tạo đối tượng MqttClient của Eclipse Paho
        // - MemoryPersistence(): Lưu trữ bộ đệm gói tin tạm thời trong RAM (không ghi file rác xuống ổ cứng)
        client = new MqttClient(effectiveUrl, clientId, new MemoryPersistence());

        // BƯỚC 4: Thiết lập các tùy chọn kết nối (MqttConnectOptions)
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);         // Phiên làm việc sạch: Không lưu lại tin nhắn cũ khi ngắt kết nối
        options.setConnectionTimeout(5);       // Thời gian chờ kết nối tối đa: 5 giây
        options.setKeepAliveInterval(20);      // Bắn gói PINGREQ mỗi 20 giây để giữ kết nối không bị ngắt
        options.setAutomaticReconnect(true);   // Tự động kết nối lại khi mạng bị chập chờn

        // Điền thông tin đăng nhập nếu có
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username.trim()); // Điền tên tài khoản
        }
        if (password != null && !password.trim().isEmpty()) {
            options.setPassword(password.trim().toCharArray()); // Điền mật khẩu
        }

        // BƯỚC 5: Đăng ký bộ lắng nghe sự kiện mạng (MqttCallbackExtended)
        client.setCallback(new MqttCallbackExtended() {
            /**
             * Sự kiện khi kết nối hoặc tái kết nối thành công tới Broker
             */
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (listener != null) {
                    // Gọi hàm onConnected() bên MainFrame để đổi nút trạng thái thành màu xanh
                    listener.onConnected(serverURI);
                }
            }

            /**
             * Sự kiện khi đường truyền bị đứt đột ngột
             */
            @Override
            public void connectionLost(Throwable cause) {
                if (listener != null) {
                    // Gọi hàm onConnectionLost() bên MainFrame để đổi trạng thái thành màu đỏ báo ngắt kết nối
                    listener.onConnectionLost(cause != null ? cause.getMessage() : "Unknown");
                }
            }

            /**
             * Sự kiện MẤU CHỐT: Khi có tin nhắn từ Broker đẩy về Client
             * @param topic: Tên chủ đề của tin nhắn (vd: esp32/sensor/dht22/data)
             * @param message: Nội dung gói tin chứa mảng byte payload
             */
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (listener != null) {
                    // Đọc nội dung byte thành chuỗi String và đẩy sang MainFrame xử lý
                    listener.onMessage(topic, new String(message.getPayload()));
                }
            }

            /**
             * Sự kiện khi gửi tin nhắn QoS 1 hoàn tất (đã nhận được PUBACK từ Broker)
             */
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Không cần xử lý thêm cho luồng này
            }
        });

        // BƯỚC 6: Bắt đầu gửi gói tin CONNECT nhị phân (0x10) lên Broker qua Socket
        client.connect(options);
    }

    /**
     * Hàm disconnect(): Chủ động ngắt kết nối với Broker
     */
    public synchronized void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect(); // Gửi gói tin DISCONNECT (0xE0) lên Broker
                client.close();      // Đóng Socket kết nối
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Hàm subscribe(): Gửi gói tin SUBSCRIBE (0x80) lên Broker để đăng ký nhận tin từ một topic
     * @param topic: Tên kênh cần đăng ký (vd: esp32/sensor/dht22/data hoặc esp32/led/command)
     * @param qos: Cấp độ đảm bảo tin nhắn (0: không cần xác nhận, 1: có gói PUBACK)
     */
    public synchronized void subscribe(String topic, int qos) throws MqttException {
        if (client != null && client.isConnected()) {
            client.subscribe(topic, qos); // Gửi gói tin SUBSCRIBE lên Broker
        }
    }

    /**
     * Hàm publish(): Gửi gói tin PUBLISH (0x30) lên Broker để phát một tin nhắn
     * @param topic: Kênh nhận tin (vd: esp32/led/command)
     * @param payload: Nội dung chữ gửi đi (vd: "ON" hoặc "OFF")
     * @param qos: Mức QoS mong muốn (QoS 0 hoặc QoS 1)
     */
    public synchronized void publish(String topic, String payload, int qos) throws MqttException {
        if (client != null && client.isConnected()) {
            // Đóng gói nội dung String thành mảng byte UTF-8
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);           // Đặt mức QoS
            msg.setRetained(false);     // Không lưu giữ (retained = false)
            client.publish(topic, msg); // Bắn gói tin lên Broker
        } else {
            // Nếu chưa kết nối mà bấm gửi thì báo lỗi
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
    }

    /**
     * Hàm kiểm tra trạng thái: Trả về true nếu client đang duy trì kết nối với Broker
     */
    public synchronized boolean isConnected() {
        return client != null && client.isConnected();
    }
}
