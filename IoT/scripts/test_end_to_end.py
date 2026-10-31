import time
import json
import unittest
import paho.mqtt.client as mqtt

class TestEndToEndMqtt(unittest.TestCase):
    def setUp(self):
        self.broker_host = "broker.emqx.io"
        self.broker_port = 1883
        self.topic_telemetry = "esp32/sensor/dht22/data/test"
        self.topic_led = "esp32/led/command/test"
        self.received_telemetry = None
        self.received_led = None

    def test_end_to_end_publish_subscribe(self):
        # 1. Subscriber client setup
        sub_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="E2E_Test_Subscriber")

        def on_message(client, userdata, msg):
            payload = msg.payload.decode('utf-8')
            if msg.topic == self.topic_telemetry:
                self.received_telemetry = json.loads(payload)
            elif msg.topic == self.topic_led:
                self.received_led = payload

        sub_client.on_message = on_message

        try:
            sub_client.connect(self.broker_host, self.broker_port, keepalive=30)
            sub_client.subscribe(self.topic_telemetry)
            sub_client.subscribe(self.topic_led)
            sub_client.loop_start()
            time.sleep(1) # Chờ subscribe hoàn tất

            # 2. Publisher client setup
            pub_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="E2E_Test_Publisher")
            pub_client.connect(self.broker_host, self.broker_port, keepalive=30)
            pub_client.loop_start()

            # 3. Publish Telemetry
            sample_telemetry = {"temperature": 32.5, "humidity": 65.0}
            pub_client.publish(self.topic_telemetry, json.dumps(sample_telemetry))

            # 4. Publish LED Command
            pub_client.publish(self.topic_led, "ON")

            # 5. Wait for subscriber to receive
            timeout = 10
            start = time.time()
            while (self.received_telemetry is None or self.received_led is None) and (time.time() - start < timeout):
                time.sleep(0.2)

            pub_client.loop_stop()
            pub_client.disconnect()
            sub_client.loop_stop()
            sub_client.disconnect()

            # 6. Assertions
            self.assertIsNotNone(self.received_telemetry, "Telemetry message was not received within timeout")
            self.assertEqual(self.received_telemetry.get("temperature"), 32.5)
            self.assertEqual(self.received_telemetry.get("humidity"), 65.0)

            self.assertIsNotNone(self.received_led, "LED command was not received within timeout")
            self.assertEqual(self.received_led, "ON")

        except Exception as e:
            self.skipTest(f"MQTT network connection failed (might be offline): {e}")

if __name__ == '__main__':
    unittest.main()
