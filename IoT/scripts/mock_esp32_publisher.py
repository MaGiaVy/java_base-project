#!/usr/bin/env python3
"""
Mô phỏng ESP32 gửi dữ liệu cảm biến DHT22 lên MQTT Broker
Hỗ trợ kiểm thử Web Dashboard và Java Client mà không cần mở trình duyệt Wokwi
Tự động phát hiện và cầu nối (bridge) nếu Windows Service và Mosquitto Local đang chạy tách biệt.
"""

import argparse
import json
import random
import threading
import time
import paho.mqtt.client as mqtt

TOPIC_DHT22 = "esp32/sensor/dht22/data"
TOPIC_LEGACY = "sensor/data"
TOPIC_STATUS = "esp32/status"
BRIDGE_TOPICS = ["sensor/#", "esp32/#", "chat/#", "test/#"]
LOOPBACK_HOSTS = ("localhost", "127.0.0.1", "::1")
DEDUP_TTL_SECONDS = 0.5


class RecentMessageCache:
    """Bộ nhớ đệm ngắn hạn tránh lặp vô hạn khi chuyển tiếp thông điệp giữa 2 broker."""

    def __init__(self, ttl_seconds=DEDUP_TTL_SECONDS):
        self.ttl_seconds = ttl_seconds
        self._seen = {}
        self._lock = threading.Lock()

    def _prune(self, now):
        expired = [key for key, ts in self._seen.items() if now - ts > self.ttl_seconds]
        for key in expired:
            del self._seen[key]

    def mark(self, topic, payload_bytes):
        """Đánh dấu thông điệp vừa được gửi đi."""
        now = time.monotonic()
        with self._lock:
            self._prune(now)
            self._seen[(topic, payload_bytes)] = now

    def check_and_mark(self, topic, payload_bytes):
        """Trả về True nếu đã thấy trong TTL; ngược lại đánh dấu mới và trả về False."""
        now = time.monotonic()
        key = (topic, payload_bytes)
        with self._lock:
            self._prune(now)
            if key in self._seen:
                return True
            self._seen[key] = now
            return False


def create_mqtt_client(client_id, username="", password="", with_will=False):
    """Khởi tạo MQTT client chuẩn paho v2."""
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    if username:
        client.username_pw_set(username, password)
    if with_will:
        client.will_set(TOPIC_STATUS, payload="Offline", qos=1, retain=True)
    return client


def detect_and_setup_loopback_bridge(primary_client, args, cache):
    """
    Kiểm tra xem localhost:1883 và 127.0.0.2:1883 có phải 2 broker tách biệt không.
    Nếu tách biệt (Windows Service giữ 127.0.0.1:1883 còn Mosquitto WebSocket giữ 0.0.0.0:1883),
    giữ kết nối phụ tới 127.0.0.2 để phát song song và chuyển tiếp hai chiều các topic người dùng.
    """
    if args.host.lower() not in LOOPBACK_HOSTS:
        return None

    probe_topic = f"system/probe/{random.randint(100000, 999999)}"
    probe_received = threading.Event()

    def on_probe(client, userdata, msg):
        if msg.topic == probe_topic:
            probe_received.set()

    primary_client.on_message = on_probe
    primary_client.subscribe(probe_topic, qos=0)
    time.sleep(0.1)

    secondary = create_mqtt_client(
        f"Mock_ESP32_Bridge_{random.randint(1000, 9999)}",
        args.user,
        args.password,
        with_will=True,
    )
    try:
        secondary.connect("127.0.0.2", args.port, keepalive=60)
        secondary.loop_start()
        secondary.publish(probe_topic, payload=b"probe", qos=0)
    except Exception:
        primary_client.unsubscribe(probe_topic)
        primary_client.on_message = None
        return None

    same_broker = probe_received.wait(timeout=0.4)
    primary_client.unsubscribe(probe_topic)

    if same_broker:
        primary_client.on_message = None
        secondary.loop_stop()
        secondary.disconnect()
        return None

    def make_forwarder(target_client, source_name):
        def on_message(client, userdata, msg):
            if cache.check_and_mark(msg.topic, msg.payload):
                return
            try:
                target_client.publish(msg.topic, payload=msg.payload, qos=msg.qos, retain=msg.retain)
            except Exception as err:
                print(f"[BRIDGE WARN] Lỗi chuyển tiếp từ {source_name} ({msg.topic}): {err}")
        return on_message

    primary_client.on_message = make_forwarder(secondary, f"{args.host}:{args.port}")
    secondary.on_message = make_forwarder(primary_client, f"127.0.0.2:{args.port}")

    for topic in BRIDGE_TOPICS:
        primary_client.subscribe(topic, qos=0)
        secondary.subscribe(topic, qos=0)

    print(
        f"[BRIDGE] Phát hiện 2 broker tách biệt trên máy: {args.host}:{args.port} (Windows Service) "
        f"và 127.0.0.2:{args.port} (Local Mosquitto/WebSocket)."
    )
    print("[BRIDGE] Đã bật đồng bộ 2 chiều cho Web Dashboard, Java Client và CMD!")
    return secondary


def publish_all(clients, cache, topic, payload, qos=0, retain=False):
    """Gửi thông điệp tới tất cả broker đang hoạt động và ghi cache chống lặp."""
    payload_bytes = payload if isinstance(payload, bytes) else str(payload).encode("utf-8")
    cache.mark(topic, payload_bytes)
    for client in clients:
        client.publish(topic, payload=payload_bytes, qos=qos, retain=retain)


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
    primary_client = create_mqtt_client(client_id, args.user, args.password, with_will=True)
    cache = RecentMessageCache()
    secondary_client = None
    mirror_client = None

    print("==================================================")
    print(" GIẢ LẬP ESP32 GỬI DỮ LIỆU CẢM BIẾN DHT22 (MOCK)")
    print("==================================================")
    print(f"Kết nối tới Broker: {args.host}:{args.port}")

    try:
        primary_client.connect(args.host, args.port, keepalive=60)
        primary_client.loop_start()

        secondary_client = detect_and_setup_loopback_bridge(primary_client, args, cache)
        if args.count == 0 and args.host.lower() in LOOPBACK_HOSTS:
            try:
                import socket
                with socket.create_connection(("broker.emqx.io", 1883), timeout=2):
                    pass
                candidate = create_mqtt_client(f"{client_id}_emqx", with_will=True)
                candidate.connect("broker.emqx.io", 1883, keepalive=60)
                candidate.loop_start()
                mirror_client = candidate
                print("[BRIDGE] Đang phát song song lên Public EMQX (broker.emqx.io:1883)")
            except Exception as exc:
                print(f"[BRIDGE] Bỏ qua Public EMQX (không kết nối được: {exc})")

        active_clients = [c for c in (primary_client, secondary_client, mirror_client) if c is not None]

        publish_all(active_clients, cache, TOPIC_STATUS, "Online", qos=1, retain=True)
        print("Đã kết nối thành công! Bắt đầu phát dữ liệu định kỳ...\n")

        sent = 0
        base_temp = 28.5
        base_hum = 62.0

        while True:
            # Sinh dữ liệu ngẫu nhiên dao động thực tế
            temp = round(base_temp + random.uniform(-1.5, 3.5), 1)
            hum = round(base_hum + random.uniform(-4.0, 4.0), 1)

            payload = json.dumps({"temperature": temp, "humidity": hum})

            publish_all(active_clients, cache, TOPIC_DHT22, payload, qos=0)
            publish_all(active_clients, cache, TOPIC_LEGACY, payload, qos=0)

            print(f"[{time.strftime('%H:%M:%S')}] MQTT -> {TOPIC_DHT22}: {payload}")

            sent += 1
            if args.count > 0 and sent >= args.count:
                break

            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\nĐã dừng mô phỏng.")
    finally:
        for client in (mirror_client, secondary_client, primary_client):
            if client is None:
                continue
            try:
                client.publish(TOPIC_STATUS, payload="Offline", qos=1, retain=True)
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
        print("Đã ngắt kết nối an toàn.")


if __name__ == "__main__":
    main()
