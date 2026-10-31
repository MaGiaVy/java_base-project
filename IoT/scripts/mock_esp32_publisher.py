#!/usr/bin/env python3
"""
Mô phỏng ESP32 gửi dữ liệu cảm biến DHT22 lên MQTT Broker
Hỗ trợ kiểm thử Web Dashboard và Java Client mà không cần mở trình duyệt Wokwi
"""

import time
import json
import random
import argparse
import paho.mqtt.client as mqtt

TOPIC_DHT22 = "esp32/sensor/dht22/data"
TOPIC_LEGACY = "sensor/data"
TOPIC_STATUS = "esp32/status"

def main():
    parser = argparse.ArgumentParser(description="Mock ESP32 DHT22 Publisher")
    parser.add_argument("--host", default="broker.emqx.io", help="MQTT Broker Host (default: broker.emqx.io)")
    parser.add_argument("--port", type=int, default=1883, help="MQTT TCP Port (default: 1883)")
    parser.add_argument("--user", default="", help="Username")
    parser.add_argument("--password", default="", help="Password")
    parser.add_argument("--interval", type=float, default=2.0, help="Publish interval in seconds")
    parser.add_argument("--count", type=int, default=0, help="Number of messages to send (0 = infinite)")
    args = parser.parse_args()

    client_id = f"Mock_ESP32_{random.randint(1000, 9999)}"
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)

    if args.user:
        client.username_pw_set(args.user, args.password)

    client.will_set(TOPIC_STATUS, payload="Offline", qos=1, retain=True)

    print("==================================================")
    print(" GIẢ LẬP ESP32 GỬI DỮ LIỆU CẢM BIẾN DHT22 (MOCK)")
    print("==================================================")
    print(f"Kết nối tới Broker: {args.host}:{args.port}")

    try:
        client.connect(args.host, args.port, keepalive=60)
        client.loop_start()
        client.publish(TOPIC_STATUS, payload="Online", qos=1, retain=True)
        print("Đã kết nối thành công! Bắt đầu phát dữ liệu định kỳ...\n")

        sent = 0
        base_temp = 28.5
        base_hum = 62.0

        while True:
            # Sinh dữ liệu ngẫu nhiên dao động thực tế
            temp = round(base_temp + random.uniform(-1.5, 3.5), 1)
            hum = round(base_hum + random.uniform(-4.0, 4.0), 1)

            payload = json.dumps({"temperature": temp, "humidity": hum})

            client.publish(TOPIC_DHT22, payload, qos=0)
            client.publish(TOPIC_LEGACY, payload, qos=0)

            print(f"[{time.strftime('%H:%M:%S')}] MQTT -> {TOPIC_DHT22}: {payload}")

            sent += 1
            if args.count > 0 and sent >= args.count:
                break

            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\nĐã dừng mô phỏng.")
    finally:
        client.publish(TOPIC_STATUS, payload="Offline", qos=1, retain=True)
        client.loop_stop()
        client.disconnect()
        print("Đã ngắt kết nối an toàn.")

if __name__ == "__main__":
    main()
