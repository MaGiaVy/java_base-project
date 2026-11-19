import unittest
import json
import time
import paho.mqtt.client as mqtt

from scripts.mock_esp32_publisher import (
    RecentMessageCache,
    create_mqtt_client,
    publish_all,
    TOPIC_DHT22,
    TOPIC_LEGACY,
    TOPIC_STATUS,
)
from scripts.mock_led_subscriber import TOPIC_LED_CMD

class TestEsp32MockComponents(unittest.TestCase):
    def test_recent_message_cache_prevents_loops(self):
        """Kiểm tra RecentMessageCache ngăn chặn vòng lặp thông điệp (message loop)"""
        cache = RecentMessageCache(ttl_seconds=0.3)
        topic = "esp32/sensor/dht22/data"
        payload = b'{"temperature": 29.5, "humidity": 60.0}'

        # Lần đầu tiên: chưa thấy -> trả về False (cho phép chuyển tiếp)
        self.assertFalse(cache.check_and_mark(topic, payload))
        # Lần thứ hai ngay lập tức: đã thấy -> trả về True (chặn để ngắt vòng lặp)
        self.assertTrue(cache.check_and_mark(topic, payload))

        # Payload khác cùng topic -> chưa thấy -> trả về False
        diff_payload = b'{"temperature": 30.0, "humidity": 61.0}'
        self.assertFalse(cache.check_and_mark(topic, diff_payload))

        # Chờ TTL hết hạn
        time.sleep(0.35)
        # Sau khi hết TTL, cùng payload -> trả về False (cho phép chuyển tiếp trở lại)
        self.assertFalse(cache.check_and_mark(topic, payload))

    def test_live_telemetry_json_schema(self):
        """Kiểm tra dữ liệu telemetry nhận được từ broker tuân thủ đúng schema JSON {"temperature": float, "humidity": float}"""
        sub_client = create_mqtt_client("Test_Telemetry_Live_Sub")
        received_msgs = []

        def on_msg(client, userdata, msg):
            received_msgs.append((msg.topic, msg.payload.decode('utf-8')))

        sub_client.on_message = on_msg
        sub_client.connect("127.0.0.1", 1883, keepalive=30)
        sub_client.subscribe([(TOPIC_DHT22, 0), (TOPIC_LEGACY, 0), (TOPIC_STATUS, 0)])
        sub_client.loop_start()

        # Thu thập trong vòng 2.5 giây để nhận ít nhất 1 chu kỳ telemetry
        start_time = time.time()
        while len([m for m in received_msgs if m[0] == TOPIC_DHT22]) == 0 and (time.time() - start_time < 4.0):
            time.sleep(0.2)

        sub_client.loop_stop()
        sub_client.disconnect()

        dht_msgs = [m[1] for m in received_msgs if m[0] == TOPIC_DHT22]
        self.assertGreater(len(dht_msgs), 0, "Không nhận được telemetry message nào trên TOPIC_DHT22 trong 4 giây")

        for payload_str in dht_msgs:
            data = json.loads(payload_str)
            self.assertIn("temperature", data)
            self.assertIn("humidity", data)
            self.assertIsInstance(data["temperature"], (int, float))
            self.assertIsInstance(data["humidity"], (int, float))
            self.assertGreaterEqual(data["temperature"], 15.0)
            self.assertLessEqual(data["temperature"], 45.0)
            self.assertGreaterEqual(data["humidity"], 20.0)
            self.assertLessEqual(data["humidity"], 99.0)

    def test_mock_led_subscriber_command_handling(self):
        """Kiểm tra việc nhận và xử lý lệnh điều khiển LED ('ON', 'OFF', '1', '0') qua topic esp32/led/command"""
        sub_client = create_mqtt_client("Test_LED_Sub")
        led_states = []

        def on_message(client, userdata, msg):
            payload = msg.payload.decode("utf-8").strip()
            if payload.upper() == "ON" or payload == "1":
                led_states.append("HIGH")
            elif payload.upper() == "OFF" or payload == "0":
                led_states.append("LOW")
            else:
                led_states.append("UNKNOWN")

        sub_client.on_message = on_message
        sub_client.connect("127.0.0.1", 1883, keepalive=30)
        sub_client.subscribe(TOPIC_LED_CMD, qos=0)
        sub_client.loop_start()
        time.sleep(0.2)

        pub_client = create_mqtt_client("Test_LED_Pub")
        pub_client.connect("127.0.0.1", 1883, keepalive=30)
        pub_client.loop_start()
        time.sleep(0.2)

        commands_to_test = ["ON", "OFF", "1", "0"]
        for cmd in commands_to_test:
            pub_client.publish(TOPIC_LED_CMD, cmd, qos=0)
            time.sleep(0.1)

        time.sleep(0.4)

        pub_client.loop_stop()
        pub_client.disconnect()
        sub_client.loop_stop()
        sub_client.disconnect()

        self.assertEqual(led_states, ["HIGH", "LOW", "HIGH", "LOW"])

    def test_dual_bridge_connectivity(self):
        """Kiểm tra kết nối tới cả 2 cổng/IP của Broker: 127.0.0.1:1883 và 127.0.0.2:1883"""
        for host in ["127.0.0.1", "127.0.0.2"]:
            client = create_mqtt_client(f"Test_DualBridge_{host.replace('.', '_')}")
            connected = [False]
            def on_connect(c, ud, f, rc, props=None):
                if rc == 0:
                    connected[0] = True
            client.on_connect = on_connect
            try:
                client.connect(host, 1883, keepalive=10)
                client.loop_start()
                time.sleep(0.3)
                client.loop_stop()
                client.disconnect()
            except Exception as e:
                self.fail(f"Không thể kết nối tới {host}:1883: {e}")
            self.assertTrue(connected[0], f"Không thể thiết lập kết nối MQTT tới {host}:1883")

if __name__ == "__main__":
    unittest.main()
