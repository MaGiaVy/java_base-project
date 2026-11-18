import base64
import hashlib
import json
import os
import random
import socket
import struct
import subprocess
import threading
import time
import unittest

import paho.mqtt.client as mqtt

def encode_remaining_length(length: int) -> bytes:
    encoded = bytearray()
    while True:
        digit = length % 128
        length //= 128
        if length > 0:
            digit |= 0x80
        encoded.append(digit)
        if length == 0:
            break
    return bytes(encoded)

def read_remaining_length(sock: socket.socket) -> int:
    multiplier = 1
    value = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise ConnectionError("Connection closed while reading remaining length")
        digit = b[0]
        value += (digit & 127) * multiplier
        multiplier *= 128
        if (digit & 128) == 0:
            break
    return value

def recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError(f"Expected {n} bytes, got {len(buf)}")
        buf.extend(chunk)
    return bytes(buf)

def recv_mqtt_packet(sock: socket.socket) -> tuple[int, int, bytes]:
    header = sock.recv(1)
    if not header:
        raise ConnectionError("Connection closed waiting for packet")
    fixed_byte = header[0]
    packet_type = (fixed_byte >> 4) & 0x0F
    flags = fixed_byte & 0x0F
    rem_len = read_remaining_length(sock)
    body = recv_exact(sock, rem_len) if rem_len > 0 else b""
    return packet_type, flags, body

def build_connect_packet(proto_name="MQTT", proto_level=4, client_id="test_client",
                         clean_session=True, keep_alive=60,
                         will_topic=None, will_payload=None, will_qos=0, will_retain=False,
                         username=None, password=None) -> bytes:
    proto_bytes = proto_name.encode('utf-8')
    vh = bytearray()
    vh.extend(struct.pack("!H", len(proto_bytes)))
    vh.extend(proto_bytes)
    vh.append(proto_level)

    flags = 0
    if clean_session:
        flags |= 0x02
    if will_topic is not None:
        flags |= 0x04
        flags |= ((will_qos & 0x03) << 3)
        if will_retain:
            flags |= 0x20
    if password is not None:
        flags |= 0x40
    if username is not None:
        flags |= 0x80
    vh.append(flags)
    vh.extend(struct.pack("!H", keep_alive))

    payload = bytearray()
    cid_bytes = client_id.encode('utf-8')
    payload.extend(struct.pack("!H", len(cid_bytes)))
    payload.extend(cid_bytes)

    if will_topic is not None:
        wt_bytes = will_topic.encode('utf-8')
        payload.extend(struct.pack("!H", len(wt_bytes)))
        payload.extend(wt_bytes)
        wp_bytes = will_payload if isinstance(will_payload, bytes) else will_payload.encode('utf-8')
        payload.extend(struct.pack("!H", len(wp_bytes)))
        payload.extend(wp_bytes)

    if username is not None:
        u_bytes = username.encode('utf-8')
        payload.extend(struct.pack("!H", len(u_bytes)))
        payload.extend(u_bytes)

    if password is not None:
        p_bytes = password.encode('utf-8')
        payload.extend(struct.pack("!H", len(p_bytes)))
        payload.extend(p_bytes)

    body = bytes(vh) + bytes(payload)
    fixed = bytes([0x10]) + encode_remaining_length(len(body))
    return fixed + body

def _wait_for_port(host, port, timeout=5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError(f"Port {host}:{port} did not open within {timeout}s")

class TestJavaMqttBrokerCompliance(unittest.TestCase):
    broker_proc = None
    broker_auth_proc = None
    tcp_port = 12883
    ws_port = 19001
    auth_tcp_port = 12884
    auth_ws_port = 19002
    benchmark_results = {}

    @classmethod
    def setUpClass(cls):
        root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        broker_dir = os.path.join(root_dir, 'java-broker')
        broker_bin = os.path.join(broker_dir, 'bin')
        broker_src = os.path.join(broker_dir, 'src', 'com', 'iot', 'broker')
        server_java = os.path.join(broker_src, 'MqttBrokerServer.java')
        frame_java = os.path.join(broker_src, 'BrokerFrame.java')

        os.makedirs(broker_bin, exist_ok=True)
        compile_cmd = ['javac', '-encoding', 'UTF-8', '-d', broker_bin, server_java, frame_java]
        res = subprocess.run(compile_cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Java compilation failed: {res.stderr}")

        cls.broker_proc = subprocess.Popen(
            ['java', '-cp', broker_bin, 'com.iot.broker.MqttBrokerServer',
             '--tcp', str(cls.tcp_port), '--ws', str(cls.ws_port)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )

        cls.broker_auth_proc = subprocess.Popen(
            ['java', '-cp', broker_bin, 'com.iot.broker.MqttBrokerServer',
             '--tcp', str(cls.auth_tcp_port), '--ws', str(cls.auth_ws_port), '--auth'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )

        _wait_for_port('127.0.0.1', cls.tcp_port)
        _wait_for_port('127.0.0.1', cls.ws_port)
        _wait_for_port('127.0.0.1', cls.auth_tcp_port)
        _wait_for_port('127.0.0.1', cls.auth_ws_port)

    @classmethod
    def tearDownClass(cls):
        for proc in (cls.broker_proc, cls.broker_auth_proc):
            if proc is not None:
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()

    # =========================================================================
    # 1. PROTOCOL LEVEL & VERSION COMPLIANCE
    # =========================================================================
    def test_01_connect_mqtt_3_1_1_success(self):
        """Verify MQTT 3.1.1 (protocol name 'MQTT', level 4) connects successfully."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(proto_name="MQTT", proto_level=4, client_id="test_311")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2, "Expected CONNACK packet (type 2)")
            self.assertEqual(len(body), 2, "CONNACK body length must be 2")
            self.assertEqual(body[0], 0x00, "Connect Acknowledge Flags should be 0x00")
            self.assertEqual(body[1], 0x00, "Return code 0x00 (Connection Accepted)")

    def test_02_connect_mqtt_3_1_success(self):
        """Verify legacy MQTT 3.1 (protocol name 'MQIsdp', level 3) connects successfully."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(proto_name="MQIsdp", proto_level=3, client_id="test_310")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2, "Expected CONNACK packet (type 2)")
            self.assertEqual(body[1], 0x00, "Return code 0x00 (Connection Accepted)")

    def test_03_connect_unsupported_protocol_rejected(self):
        """Verify invalid protocol version returns CONNACK return code 0x01 (unacceptable protocol version)."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(proto_name="MQTT", proto_level=5, client_id="test_invalid_proto")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2, "Expected CONNACK packet")
            self.assertEqual(body[1], 0x01, "Return code 0x01 (Unacceptable protocol version)")

    # =========================================================================
    # 2. AUTHENTICATION & CREDENTIAL VERIFICATION
    # =========================================================================
    def test_04_auth_valid_credentials_accepted(self):
        """When broker requires auth, user1/123 is accepted."""
        with socket.create_connection(('127.0.0.1', self.auth_tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="auth_good", username="user1", password="123")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x00, "Return code 0x00 (Accepted)")

    def test_05_auth_alternate_password_accepted(self):
        """When broker requires auth, user1/123456 is also accepted."""
        with socket.create_connection(('127.0.0.1', self.auth_tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="auth_good2", username="user1", password="123456")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x00, "Return code 0x00 (Accepted)")

    def test_06_auth_invalid_credentials_rejected(self):
        """When broker requires auth, bad password returns CONNACK 0x04 (Bad user or password)."""
        with socket.create_connection(('127.0.0.1', self.auth_tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="auth_bad", username="user1", password="wrongpassword")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x04, "Return code 0x04 (Bad user name or password)")

    def test_07_auth_missing_credentials_rejected(self):
        """When broker requires auth, anonymous connect returns CONNACK 0x04."""
        with socket.create_connection(('127.0.0.1', self.auth_tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="auth_anon")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x04, "Return code 0x04 (Bad user name or password)")

    def test_08_anonymous_accepted_when_auth_disabled(self):
        """When auth is disabled, anonymous connection is accepted."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="anon_client")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x00, "Connection accepted anonymously")

    def test_09_auto_client_id_when_empty(self):
        """When client ID is empty string, broker auto-assigns client ID and accepts connection."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            pkt = build_connect_packet(client_id="")
            s.sendall(pkt)
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)
            self.assertEqual(body[1], 0x00, "Connection accepted with auto client ID")

    # =========================================================================
    # 3. PINGREQ & PINGRESP (KEEP-ALIVE)
    # =========================================================================
    def test_10_pingreq_pingresp(self):
        """Verify broker responds with PINGRESP (0xD0 0x00) when PINGREQ (0xC0 0x00) is sent."""
        with socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0) as s:
            s.sendall(build_connect_packet(client_id="ping_test"))
            ptype, _, _ = recv_mqtt_packet(s)
            self.assertEqual(ptype, 2)

            # Send PINGREQ
            s.sendall(b'\xc0\x00')
            ptype, flags, body = recv_mqtt_packet(s)
            self.assertEqual(ptype, 13, "Expected PINGRESP (type 13 / 0xD0)")
            self.assertEqual(len(body), 0, "PINGRESP has zero payload")

    # =========================================================================
    # 4. PUBLISH & PUBACK (QoS 0, QoS 1, PACKET ID TRACKING)
    # =========================================================================
    def test_11_publish_qos0_and_qos1_flow(self):
        """Verify QoS 0 and QoS 1 delivery, PUBACK return with matching packet ID."""
        received = []
        sub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="test_sub_qos")
        sub.on_message = lambda c, u, m: received.append({
            'topic': m.topic,
            'payload': m.payload.decode('utf-8'),
            'qos': m.qos,
            'retain': m.retain
        })
        sub.connect('127.0.0.1', self.tcp_port, keepalive=30)
        sub.subscribe("test/qos/#", qos=1)
        sub.loop_start()
        time.sleep(0.2)

        pub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="test_pub_qos")
        pub.connect('127.0.0.1', self.tcp_port, keepalive=30)
        pub.loop_start()

        # QoS 0 Publish
        info0 = pub.publish("test/qos/zero", "PayloadQoS0", qos=0)
        info0.wait_for_publish(timeout=2.0)

        # QoS 1 Publish (waits for PUBACK)
        info1 = pub.publish("test/qos/one", "PayloadQoS1", qos=1)
        info1.wait_for_publish(timeout=2.0)
        self.assertTrue(info1.is_published(), "PUBACK must be received by paho client for QoS 1")

        # Wait for subscriber
        deadline = time.time() + 3.0
        while time.time() < deadline and len(received) < 2:
            time.sleep(0.05)

        sub.loop_stop(); sub.disconnect()
        pub.loop_stop(); pub.disconnect()

        self.assertEqual(len(received), 2)
        qos0_msg = next((m for m in received if m['topic'] == "test/qos/zero"), None)
        qos1_msg = next((m for m in received if m['topic'] == "test/qos/one"), None)
        self.assertIsNotNone(qos0_msg)
        self.assertEqual(qos0_msg['payload'], "PayloadQoS0")
        self.assertEqual(qos0_msg['qos'], 0)

        self.assertIsNotNone(qos1_msg)
        self.assertEqual(qos1_msg['payload'], "PayloadQoS1")
        self.assertEqual(qos1_msg['qos'], 1)

    # =========================================================================
    # 5. SUBSCRIBE & WILDCARDS (+ and #)
    # =========================================================================
    def test_12_wildcard_matching(self):
        """Verify single-level '+' and multi-level '#' wildcards route correctly."""
        msgs_plus = []
        msgs_hash = []

        c_plus = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="client_plus")
        c_plus.on_message = lambda c, u, m: msgs_plus.append((m.topic, m.payload.decode('utf-8')))
        c_plus.connect('127.0.0.1', self.tcp_port)
        c_plus.subscribe("esp32/+/command")
        c_plus.loop_start()

        c_hash = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="client_hash")
        c_hash.on_message = lambda c, u, m: msgs_hash.append((m.topic, m.payload.decode('utf-8')))
        c_hash.connect('127.0.0.1', self.tcp_port)
        c_hash.subscribe("sensor/#")
        c_hash.loop_start()

        time.sleep(0.2)

        pub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="client_pub_wc")
        pub.connect('127.0.0.1', self.tcp_port)
        pub.loop_start()

        pub.publish("esp32/led/command", "LED_ON").wait_for_publish(timeout=2.0)
        pub.publish("esp32/relay/command", "RELAY_OFF").wait_for_publish(timeout=2.0)
        pub.publish("esp32/sub/device/command", "INVALID").wait_for_publish(timeout=2.0)

        pub.publish("sensor/dht22/temperature", "28.4").wait_for_publish(timeout=2.0)
        pub.publish("sensor/soil/moisture/level1", "65%").wait_for_publish(timeout=2.0)
        pub.publish("other/topic", "IGNORED").wait_for_publish(timeout=2.0)

        time.sleep(0.5)

        c_plus.loop_stop(); c_plus.disconnect()
        c_hash.loop_stop(); c_hash.disconnect()
        pub.loop_stop(); pub.disconnect()

        self.assertIn(("esp32/led/command", "LED_ON"), msgs_plus)
        self.assertIn(("esp32/relay/command", "RELAY_OFF"), msgs_plus)
        self.assertNotIn(("esp32/sub/device/command", "INVALID"), msgs_plus)

        self.assertIn(("sensor/dht22/temperature", "28.4"), msgs_hash)
        self.assertIn(("sensor/soil/moisture/level1", "65%"), msgs_hash)
        self.assertNotIn(("other/topic", "IGNORED"), msgs_hash)

    # =========================================================================
    # 6. RETAINED MESSAGE BEHAVIOR & CLEARING
    # =========================================================================
    def test_13_retained_message_lifecycle(self):
        """Verify retain=True stores message, late subscriber receives it, empty payload clears it."""
        pub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="retain_pub")
        pub.connect('127.0.0.1', self.tcp_port)
        pub.loop_start()

        # 1. Publish retained message
        pub.publish("device/state", "ACTIVE", retain=True).wait_for_publish(timeout=2.0)

        # 2. Late subscriber connects and subscribes
        received = []
        sub1 = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="retain_sub1")
        sub1.on_message = lambda c, u, m: received.append((m.topic, m.payload.decode('utf-8'), m.retain))
        sub1.connect('127.0.0.1', self.tcp_port)
        sub1.subscribe("device/state")
        sub1.loop_start()

        deadline = time.time() + 3.0
        while time.time() < deadline and not received:
            time.sleep(0.05)

        sub1.loop_stop(); sub1.disconnect()
        self.assertEqual(received, [("device/state", "ACTIVE", True)],
                         "Retained message must be received with retain flag True")

        # 3. Clear retained message by publishing empty payload with retain=True
        pub.publish("device/state", "", retain=True).wait_for_publish(timeout=2.0)
        time.sleep(0.2)

        # 4. Another late subscriber connects; should receive NOTHING
        received2 = []
        sub2 = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="retain_sub2")
        sub2.on_message = lambda c, u, m: received2.append((m.topic, m.payload.decode('utf-8')))
        sub2.connect('127.0.0.1', self.tcp_port)
        sub2.subscribe("device/state")
        sub2.loop_start()
        time.sleep(0.5)

        sub2.loop_stop(); sub2.disconnect()
        pub.loop_stop(); pub.disconnect()

        self.assertEqual(len(received2), 0, "Cleared retained message must not be delivered to new subscribers")

    # =========================================================================
    # 7. UNSUBSCRIBE & UNSUBACK
    # =========================================================================
    def test_14_unsubscribe_unsuback_and_stop_delivery(self):
        """Verify UNSUBSCRIBE produces UNSUBACK and cancels message routing."""
        received = []
        unsub_event = threading.Event()

        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="test_unsub")
        client.on_message = lambda c, u, m: received.append(m.payload.decode('utf-8'))
        client.on_unsubscribe = lambda *args: unsub_event.set()

        client.connect('127.0.0.1', self.tcp_port)
        client.subscribe("test/unsub/topic")
        client.loop_start()
        time.sleep(0.2)

        pub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="test_unsub_pub")
        pub.connect('127.0.0.1', self.tcp_port)
        pub.loop_start()

        pub.publish("test/unsub/topic", "FIRST").wait_for_publish(timeout=2.0)
        time.sleep(0.2)
        self.assertIn("FIRST", received)

        # Unsubscribe
        client.unsubscribe("test/unsub/topic")
        self.assertTrue(unsub_event.wait(timeout=2.0), "UNSUBACK must be received")

        # Publish second message
        pub.publish("test/unsub/topic", "SECOND").wait_for_publish(timeout=2.0)
        time.sleep(0.3)

        client.loop_stop(); client.disconnect()
        pub.loop_stop(); pub.disconnect()

        self.assertNotIn("SECOND", received, "Message published after unsubscribe must not be delivered")

    # =========================================================================
    # 8. DISCONNECT & LWT (LAST WILL AND TESTAMENT)
    # =========================================================================
    def test_15_lwt_fired_on_ungraceful_disconnect(self):
        """When socket closes abruptly without DISCONNECT packet, LWT is dispatched to subscribers."""
        lwt_msgs = []
        monitor = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="lwt_monitor")
        monitor.on_message = lambda c, u, m: lwt_msgs.append((m.topic, m.payload.decode('utf-8'), m.retain))
        monitor.connect('127.0.0.1', self.tcp_port)
        monitor.subscribe("device/lwt/status", qos=1)
        monitor.loop_start()
        time.sleep(0.2)

        # Raw client connects with LWT configured
        sock = socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0)
        pkt = build_connect_packet(
            client_id="lwt_crasher",
            will_topic="device/lwt/status",
            will_payload="Device Abruptly Lost",
            will_qos=1,
            will_retain=False
        )
        sock.sendall(pkt)
        ptype, _, _ = recv_mqtt_packet(sock)
        self.assertEqual(ptype, 2, "LWT client connected")

        # Close socket abruptly WITHOUT DISCONNECT packet
        sock.close()

        deadline = time.time() + 3.0
        while time.time() < deadline and not lwt_msgs:
            time.sleep(0.05)

        monitor.loop_stop(); monitor.disconnect()

        self.assertEqual(len(lwt_msgs), 1, "Broker must publish LWT on ungraceful disconnect")
        self.assertEqual(lwt_msgs[0][0], "device/lwt/status")
        self.assertEqual(lwt_msgs[0][1], "Device Abruptly Lost")

    def test_16_lwt_suppressed_on_graceful_disconnect(self):
        """When client sends DISCONNECT packet before closing socket, LWT must NOT be published."""
        lwt_msgs = []
        monitor = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="lwt_monitor_graceful")
        monitor.on_message = lambda c, u, m: lwt_msgs.append(m.payload.decode('utf-8'))
        monitor.connect('127.0.0.1', self.tcp_port)
        monitor.subscribe("device/lwt/graceful", qos=1)
        monitor.loop_start()
        time.sleep(0.2)

        sock = socket.create_connection(('127.0.0.1', self.tcp_port), timeout=2.0)
        pkt = build_connect_packet(
            client_id="lwt_graceful_client",
            will_topic="device/lwt/graceful",
            will_payload="Device Lost",
            will_qos=1
        )
        sock.sendall(pkt)
        ptype, _, _ = recv_mqtt_packet(sock)
        self.assertEqual(ptype, 2)

        # Send clean DISCONNECT packet (0xE0 0x00)
        sock.sendall(b'\xe0\x00')
        time.sleep(0.1)
        sock.close()

        time.sleep(0.5)
        monitor.loop_stop(); monitor.disconnect()

        self.assertEqual(len(lwt_msgs), 0, "LWT must NOT fire when DISCONNECT packet is sent")

    # =========================================================================
    # 9. WEBSOCKET LISTENER RFC 6455 HANDSHAKE & FRAMING
    # =========================================================================
    def test_17_websocket_rfc6455_handshake_and_framing(self):
        """Verify RFC 6455 HTTP upgrade handshake, Sec-WebSocket-Accept, and binary frame unmasking."""
        raw_sock = socket.create_connection(('127.0.0.1', self.ws_port), timeout=2.0)
        raw_key = base64.b64encode(os.urandom(16)).decode('ascii')
        expected_accept = base64.b64encode(
            hashlib.sha1((raw_key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode('utf-8')).digest()
        ).decode('ascii')

        req = (
            f"GET / HTTP/1.1\r\n"
            f"Host: 127.0.0.1:{self.ws_port}\r\n"
            f"Upgrade: websocket\r\n"
            f"Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {raw_key}\r\n"
            f"Sec-WebSocket-Version: 13\r\n"
            f"Sec-WebSocket-Protocol: mqtt\r\n\r\n"
        )
        raw_sock.sendall(req.encode('ascii'))

        resp = bytearray()
        while b"\r\n\r\n" not in resp:
            chunk = raw_sock.recv(512)
            if not chunk:
                break
            resp.extend(chunk)

        header_text = resp.decode('utf-8', errors='ignore')
        self.assertIn("HTTP/1.1 101 Switching Protocols", header_text)
        self.assertIn(f"Sec-WebSocket-Accept: {expected_accept}", header_text)
        self.assertIn("Sec-WebSocket-Protocol: mqtt", header_text)

        # Send masked binary MQTT CONNECT frame over WebSocket
        mqtt_connect = build_connect_packet(client_id="ws_raw_client")
        mask = os.urandom(4)
        masked_payload = bytes(b ^ mask[i % 4] for i, b in enumerate(mqtt_connect))

        ws_frame = bytearray([0x82, 0x80 | len(mqtt_connect)])
        ws_frame.extend(mask)
        ws_frame.extend(masked_payload)
        raw_sock.sendall(bytes(ws_frame))

        # Read WebSocket response frame (Broker to Client is unmasked)
        ws_resp_b0 = raw_sock.recv(1)[0]
        ws_resp_b1 = raw_sock.recv(1)[0]
        self.assertEqual(ws_resp_b0, 0x82, "Server response must be binary frame (0x82)")
        resp_len = ws_resp_b1 & 0x7F
        resp_payload = recv_exact(raw_sock, resp_len)

        self.assertEqual(resp_payload[0], 0x20, "Fixed header CONNACK")
        self.assertEqual(resp_payload[1], 0x02)
        self.assertEqual(resp_payload[3], 0x00, "Connection Accepted")
        raw_sock.close()

    # =========================================================================
    # 10. CROSS-TRANSPORT BRIDGING (TCP <-> WEBSOCKET)
    # =========================================================================
    def test_18_cross_transport_bidirectional_routing(self):
        """Verify seamless bidirectional routing: TCP publisher -> WS subscriber & WS publisher -> TCP subscriber."""
        ws_msgs = []
        tcp_msgs = []
        ws_connected = threading.Event()
        tcp_connected = threading.Event()
        ws_suback = threading.Event()
        tcp_suback = threading.Event()

        ws_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="cross_ws", transport="websockets")
        ws_client.ws_set_options(path="/")
        ws_client.on_connect = lambda *args: ws_connected.set()
        ws_client.on_subscribe = lambda *args: ws_suback.set()
        ws_client.on_message = lambda c, u, m: ws_msgs.append((m.topic, m.payload.decode('utf-8')))

        tcp_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="cross_tcp", transport="tcp")
        tcp_client.on_connect = lambda *args: tcp_connected.set()
        tcp_client.on_subscribe = lambda *args: tcp_suback.set()
        tcp_client.on_message = lambda c, u, m: tcp_msgs.append((m.topic, m.payload.decode('utf-8')))

        ws_client.connect('127.0.0.1', self.ws_port)
        tcp_client.connect('127.0.0.1', self.tcp_port)
        ws_client.loop_start()
        tcp_client.loop_start()

        self.assertTrue(ws_connected.wait(timeout=4.0), "WS client failed to connect")
        self.assertTrue(tcp_connected.wait(timeout=4.0), "TCP client failed to connect")

        ws_client.subscribe("cross/to_ws", qos=1)
        tcp_client.subscribe("cross/to_tcp", qos=1)
        self.assertTrue(ws_suback.wait(timeout=3.0), "WS SUBACK failed")
        self.assertTrue(tcp_suback.wait(timeout=3.0), "TCP SUBACK failed")

        # TCP publishes to WS
        tcp_client.publish("cross/to_ws", "Hello From TCP", qos=1).wait_for_publish(timeout=2.0)
        # WS publishes to TCP
        ws_client.publish("cross/to_tcp", "Hello From WebSocket", qos=1).wait_for_publish(timeout=2.0)

        deadline = time.time() + 3.0
        while time.time() < deadline and (not ws_msgs or not tcp_msgs):
            time.sleep(0.05)

        ws_client.loop_stop(); ws_client.disconnect()
        tcp_client.loop_stop(); tcp_client.disconnect()

        self.assertIn(("cross/to_ws", "Hello From TCP"), ws_msgs)
        self.assertIn(("cross/to_tcp", "Hello From WebSocket"), tcp_msgs)

    # =========================================================================
    # 11. PERFORMANCE & STRESS BENCHMARK (LATENCY, DELIVERY RATE, PACKET DROPS)
    # =========================================================================
    def test_19_performance_and_zero_packet_drops(self):
        """Stress test: 500 messages sent across TCP and WebSocket.
        Measures: delivery rate (100%), packet drops (0), latency (min/avg/max/p95), throughput (msg/s)."""
        NUM_MESSAGES = 500
        received_map = {}
        received_lock = threading.Lock()
        sub_connected = threading.Event()
        sub_subscribed = threading.Event()

        sub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="perf_sub", transport="websockets")
        sub.ws_set_options(path="/")
        sub.max_inflight_messages_set(200)

        def on_msg(client, userdata, msg):
            recv_time = time.perf_counter()
            data = json.loads(msg.payload.decode('utf-8'))
            seq = data['seq']
            send_time = data['send_time']
            latency_ms = (recv_time - send_time) * 1000.0
            with received_lock:
                received_map[seq] = latency_ms

        sub.on_connect = lambda *args: sub_connected.set()
        sub.on_subscribe = lambda *args: sub_subscribed.set()
        sub.on_message = on_msg

        sub.connect('127.0.0.1', self.ws_port)
        sub.loop_start()
        self.assertTrue(sub_connected.wait(timeout=4.0), "Benchmark subscriber failed to connect")

        sub.subscribe("perf/test/#", qos=1)
        self.assertTrue(sub_subscribed.wait(timeout=4.0), "Benchmark subscriber failed to subscribe")

        pub = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="perf_pub", transport="tcp")
        pub.max_inflight_messages_set(200)
        pub_connected = threading.Event()
        pub.on_connect = lambda *args: pub_connected.set()
        pub.connect('127.0.0.1', self.tcp_port)
        pub.loop_start()
        self.assertTrue(pub_connected.wait(timeout=4.0), "Benchmark publisher failed to connect")

        start_time = time.perf_counter()
        for i in range(NUM_MESSAGES):
            payload = json.dumps({'seq': i, 'send_time': time.perf_counter()})
            pub.publish(f"perf/test/{i % 5}", payload, qos=0)
            if i % 25 == 0:
                time.sleep(0.005)

        deadline = time.time() + 10.0
        while time.time() < deadline:
            with received_lock:
                if len(received_map) >= NUM_MESSAGES:
                    break
            time.sleep(0.02)

        total_elapsed = time.perf_counter() - start_time

        pub.loop_stop(); pub.disconnect()
        sub.loop_stop(); sub.disconnect()

        with received_lock:
            received_count = len(received_map)
            latencies = list(received_map.values())

        delivery_rate = (received_count / NUM_MESSAGES) * 100.0
        dropped_packets = NUM_MESSAGES - received_count

        latencies.sort()
        min_lat = latencies[0] if latencies else 0.0
        max_lat = latencies[-1] if latencies else 0.0
        avg_lat = sum(latencies) / len(latencies) if latencies else 0.0
        p95_lat = latencies[int(len(latencies) * 0.95)] if latencies else 0.0
        throughput = received_count / total_elapsed if total_elapsed > 0 else 0.0

        TestJavaMqttBrokerCompliance.benchmark_results = {
            'total_sent': NUM_MESSAGES,
            'received_count': received_count,
            'dropped_packets': dropped_packets,
            'delivery_rate_pct': delivery_rate,
            'throughput_msg_per_sec': round(throughput, 1),
            'min_latency_ms': round(min_lat, 2),
            'avg_latency_ms': round(avg_lat, 2),
            'p95_latency_ms': round(p95_lat, 2),
            'max_latency_ms': round(max_lat, 2)
        }

        self.assertEqual(dropped_packets, 0, f"Expected 0 packet drops, got {dropped_packets}")
        self.assertEqual(delivery_rate, 100.0, f"Expected 100% delivery rate, got {delivery_rate}%")

if __name__ == '__main__':
    suite = unittest.TestLoader().loadTestsFromTestCase(TestJavaMqttBrokerCompliance)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    if hasattr(TestJavaMqttBrokerCompliance, 'benchmark_results'):
        print("\n=== BENCHMARK METRICS ===")
        print(json.dumps(TestJavaMqttBrokerCompliance.benchmark_results, indent=2))
