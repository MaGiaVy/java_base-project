#!/usr/bin/env python3
"""
Mô phỏng ESP32 nhận lệnh điều khiển đèn LED từ Web Dashboard hoặc Java Client
"""

import time
import argparse
import paho.mqtt.client as mqtt

TOPIC_LED_CMD = "esp32/led/command"

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print("[MOCK ESP32] Kết nối Broker thành công!")
        client.subscribe(TOPIC_LED_CMD)
        print(f"[MOCK ESP32] Đang lắng nghe lệnh tại topic: '{TOPIC_LED_CMD}'...")
    else:
        print(f"[MOCK ESP32] Kết nối thất bại, mã rc={rc}")

def on_message(client, userdata, msg):
    payload = msg.payload.decode("utf-8").strip()
    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] Nhận lệnh trên '{msg.topic}': '{payload}'")

    if payload.upper() == "ON" or payload == "1":
        print("  💡 >>> [ESP32 VẬT LÝ] ĐÈN LED ĐỎ: BẬT SÁNG (HIGH) <<<")
    elif payload.upper() == "OFF" or payload == "0":
        print("  🌑 >>> [ESP32 VẬT LÝ] ĐÈN LED ĐỎ: TẮT (LOW) <<<")
    else:
        print(f"  ❓ Lệnh không xác định: '{payload}'")

def main():
    parser = argparse.ArgumentParser(description="Mock ESP32 LED Subscriber")
    parser.add_argument("--host", default="broker.emqx.io", help="MQTT Broker Host")
    parser.add_argument("--port", type=int, default=1883, help="MQTT TCP Port")
    parser.add_argument("--user", default="", help="Username")
    parser.add_argument("--password", default="", help="Password")
    args = parser.parse_args()

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="Mock_ESP32_LED_Receiver")
    if args.user:
        client.username_pw_set(args.user, args.password)

    client.on_connect = on_connect
    client.on_message = on_message

    print("==================================================")
    print(" GIẢ LẬP ESP32 NHẬN LỆNH ĐIỀU KHIỂN ĐÈN LED (MOCK)")
    print("==================================================")
    print(f"Đang kết nối {args.host}:{args.port}...")

    try:
        client.connect(args.host, args.port, keepalive=60)
        client.loop_forever()
    except KeyboardInterrupt:
        print("\nĐã dừng mô phỏng.")
        client.disconnect()

if __name__ == "__main__":
    main()
