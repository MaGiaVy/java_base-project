#!/usr/bin/env python3
"""
Verification Script: End-Client Experience for Java Swing Client & Web Dashboard.
1. Simultaneous subscriber clients:
   - Client A (Java Client): TCP on 127.0.0.2:1883
   - Client B (Web Dashboard): WebSocket on ws://127.0.0.1:9001/
2. Measure simultaneous telemetry reception (~2s interval).
3. Verify fluctuating temperature and humidity values.
4. Perform live LED command dispatch test ('ON' / 'OFF') and measure latency.
"""

import json
import time
import threading
import paho.mqtt.client as mqtt

TCP_HOST = "127.0.0.2"
TCP_PORT = 1883
WS_HOST = "127.0.0.1"
WS_PORT = 9001
WS_PATH = "/"

TOPIC_TELEMETRY = "esp32/sensor/dht22/data"
TOPIC_LED = "esp32/led/command"
TOPIC_STATUS = "esp32/status"

tcp_telemetry_events = []
ws_telemetry_events = []

led_events = {
    "tcp_received": [],
    "ws_received": []
}

lock = threading.Lock()

# 1. Setup Java Client (TCP)
def on_tcp_message(client, userdata, msg):
    arrival_time = time.time()
    payload_str = msg.payload.decode('utf-8')
    with lock:
        if msg.topic == TOPIC_TELEMETRY:
            tcp_telemetry_events.append({
                "time": arrival_time,
                "payload": payload_str
            })
        elif msg.topic == TOPIC_LED:
            led_events["tcp_received"].append({
                "time": arrival_time,
                "payload": payload_str
            })

client_tcp = mqtt.Client(
    mqtt.CallbackAPIVersion.VERSION2,
    client_id="Java_Client_Verification"
)
client_tcp.on_message = on_tcp_message

# 2. Setup Web Dashboard (WebSocket)
def on_ws_message(client, userdata, msg):
    arrival_time = time.time()
    payload_str = msg.payload.decode('utf-8')
    with lock:
        if msg.topic == TOPIC_TELEMETRY:
            ws_telemetry_events.append({
                "time": arrival_time,
                "payload": payload_str
            })
        elif msg.topic == TOPIC_LED:
            led_events["ws_received"].append({
                "time": arrival_time,
                "payload": payload_str
            })

client_ws = mqtt.Client(
    mqtt.CallbackAPIVersion.VERSION2,
    client_id="Web_Dashboard_Verification",
    transport="websockets"
)
client_ws.ws_set_options(path=WS_PATH)
client_ws.on_message = on_ws_message

print(f"[*] Connecting Java Client (TCP) to {TCP_HOST}:{TCP_PORT}...")
client_tcp.connect(TCP_HOST, TCP_PORT, keepalive=60)
client_tcp.subscribe([(TOPIC_TELEMETRY, 0), (TOPIC_LED, 0), (TOPIC_STATUS, 0)])
client_tcp.loop_start()

print(f"[*] Connecting Web Dashboard (WS) to ws://{WS_HOST}:{WS_PORT}{WS_PATH}...")
client_ws.connect(WS_HOST, WS_PORT, keepalive=60)
client_ws.subscribe([(TOPIC_TELEMETRY, 0), (TOPIC_LED, 0), (TOPIC_STATUS, 0)])
client_ws.loop_start()

time.sleep(1.0)
print("[*] Both clients connected and subscribed successfully.\n")

# Collect 6 telemetry samples (~12 seconds)
print("[*] Collecting live telemetry for ~12 seconds...")
target_samples = 6
start_collect = time.time()
while time.time() - start_collect < 14:
    with lock:
        if len(tcp_telemetry_events) >= target_samples and len(ws_telemetry_events) >= target_samples:
            break
    time.sleep(0.5)

print(f"[*] Collected {len(tcp_telemetry_events)} TCP messages and {len(ws_telemetry_events)} WS messages.\n")

# 4. Perform live LED command dispatch test
print("[*] Testing LED dispatch: Web Dashboard -> Java Client (Publish 'ON')...")
t_send_on = time.time()
client_ws.publish(TOPIC_LED, "ON", qos=0)

# Wait for TCP client to receive
received_on = False
for _ in range(50):
    time.sleep(0.05)
    with lock:
        if any(e["payload"] == "ON" for e in led_events["tcp_received"]):
            received_on = True
            break

t_recv_on = None
if received_on:
    for e in led_events["tcp_received"]:
        if e["payload"] == "ON":
            t_recv_on = e["time"]
            break
latency_on_ms = (t_recv_on - t_send_on) * 1000 if t_recv_on else -1

print("[*] Testing LED dispatch: Java Client -> Web Dashboard (Publish 'OFF')...")
t_send_off = time.time()
client_tcp.publish(TOPIC_LED, "OFF", qos=0)

# Wait for WS client to receive
received_off = False
for _ in range(50):
    time.sleep(0.05)
    with lock:
        if any(e["payload"] == "OFF" for e in led_events["ws_received"]):
            received_off = True
            break

t_recv_off = None
if received_off:
    for e in led_events["ws_received"]:
        if e["payload"] == "OFF":
            t_recv_off = e["time"]
            break
latency_off_ms = (t_recv_off - t_send_off) * 1000 if t_recv_off else -1

# Clean disconnect
client_tcp.loop_stop()
client_ws.loop_stop()
client_tcp.disconnect()
client_ws.disconnect()

# Print Comprehensive Analysis
print("======================================================================")
print("              END-CLIENT VERIFICATION REPORT")
print("======================================================================")

print("\n--- 1. Telemetry Samples & Simultaneity Analysis ---")
min_len = min(len(tcp_telemetry_events), len(ws_telemetry_events))
print(f"{'Sample #':<10}{'TCP Time':<15}{'WS Time':<15}{'Diff (ms)':<12}{'TCP Temp':<10}{'TCP Hum':<10}{'WS Temp':<10}{'WS Hum':<10}")
print("-" * 85)

temp_values = []
hum_values = []
intervals_tcp = []

for i in range(min_len):
    t_tcp = tcp_telemetry_events[i]["time"]
    t_ws = ws_telemetry_events[i]["time"]
    diff_ms = abs(t_ws - t_tcp) * 1000

    d_tcp = json.loads(tcp_telemetry_events[i]["payload"])
    d_ws = json.loads(ws_telemetry_events[i]["payload"])

    temp_values.append(d_tcp.get("temperature"))
    hum_values.append(d_tcp.get("humidity"))

    if i > 0:
        intervals_tcp.append(t_tcp - tcp_telemetry_events[i-1]["time"])

    time_tcp_str = time.strftime('%H:%M:%S', time.localtime(t_tcp)) + f".{int((t_tcp % 1)*1000):03d}"
    time_ws_str = time.strftime('%H:%M:%S', time.localtime(t_ws)) + f".{int((t_ws % 1)*1000):03d}"

    print(f"{i+1:<10}{time_tcp_str:<15}{time_ws_str:<15}{diff_ms:<12.2f}{d_tcp.get('temperature'):<10}{d_tcp.get('humidity'):<10}{d_ws.get('temperature'):<10}{d_ws.get('humidity'):<10}")

print("-" * 85)
avg_interval = sum(intervals_tcp) / len(intervals_tcp) if intervals_tcp else 0
print(f"Average telemetry interval: {avg_interval:.2f} seconds (Target: ~2.0s)")

# Fluctuating values check
is_fluctuating = len(set(temp_values)) > 1 and len(set(hum_values)) > 1
print(f"Temperature values observed: {temp_values}")
print(f"Humidity values observed:    {hum_values}")
print(f"Telemetry fluctuating check: {'PASSED (Values change live)' if is_fluctuating else 'FAILED (Static)'}")

print("\n--- 2. LED Command Dispatch & Round-trip Latency ---")
print(f"Command 'ON'  (WS -> TCP): Received = {received_on}, Latency = {latency_on_ms:.2f} ms")
print(f"Command 'OFF' (TCP -> WS): Received = {received_off}, Latency = {latency_off_ms:.2f} ms")
avg_latency = (latency_on_ms + latency_off_ms) / 2 if (received_on and received_off) else -1
print(f"Average LED Dispatch Latency: {avg_latency:.2f} ms")

print("\n======================================================================")
print("                    VERIFICATION SUMMARY")
print("======================================================================")
all_passed = (min_len >= 4) and is_fluctuating and received_on and received_off
print(f"Overall Status: {'PASSED' if all_passed else 'FAILED'}")
