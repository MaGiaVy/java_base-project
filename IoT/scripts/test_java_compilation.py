import os
import socket
import subprocess
import threading
import time
import unittest

import paho.mqtt.client as mqtt


def _create_mqtt_client(client_id, transport="tcp", username=None, password=None):
    cb_ver = getattr(mqtt, "CallbackAPIVersion", None)
    if cb_ver is not None:
        client = mqtt.Client(cb_ver.VERSION2, client_id=client_id, transport=transport)
    else:
        client = mqtt.Client(client_id=client_id, transport=transport)
    if transport == "websockets":
        client.ws_set_options(path="/")
    if username:
        client.username_pw_set(username, password)
    return client


def _wait_for_port(host, port, timeout=5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise TimeoutError(f"Port {host}:{port} did not open within {timeout}s")


class TestJavaCompilation(unittest.TestCase):
    def setUp(self):
        self.root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        self.java_dir = os.path.join(self.root_dir, 'java-client')
        self.lib_dir = os.path.join(self.java_dir, 'lib')
        self.src_dir = os.path.join(self.java_dir, 'src', 'com', 'iot')
        self.jar_path = os.path.join(self.lib_dir, 'org.eclipse.paho.client.mqttv3-1.2.5.jar')

        self.broker_dir = os.path.join(self.root_dir, 'java-broker')
        self.broker_bin = os.path.join(self.broker_dir, 'bin')
        self.broker_src = os.path.join(self.broker_dir, 'src', 'com', 'iot', 'broker')

    def test_java_files_and_jar_exist(self):
        self.assertTrue(os.path.exists(self.jar_path), f"Paho JAR not found at {self.jar_path}")
        app_java = os.path.join(self.src_dir, 'App.java')
        main_java = os.path.join(self.src_dir, 'MainFrame.java')
        mqtt_java = os.path.join(self.src_dir, 'MqttManager.java')
        self.assertTrue(os.path.exists(app_java), "App.java missing")
        self.assertTrue(os.path.exists(main_java), "MainFrame.java missing")
        self.assertTrue(os.path.exists(mqtt_java), "MqttManager.java missing")

    def test_javac_compilation(self):
        if not os.path.exists(self.jar_path):
            self.skipTest("JAR not downloaded yet")

        src_files = [
            os.path.join(self.src_dir, 'MqttManager.java'),
            os.path.join(self.src_dir, 'MainFrame.java'),
            os.path.join(self.src_dir, 'App.java')
        ]
        cmd = ['javac', '-encoding', 'UTF-8', '-cp', f'{self.jar_path};{self.src_dir}', *src_files]
        res = subprocess.run(cmd, capture_output=True, text=True)
        self.assertEqual(res.returncode, 0, f"Compilation failed with error: {res.stderr}")

    def test_java_broker_compilation_and_routing(self):
        server_java = os.path.join(self.broker_src, 'MqttBrokerServer.java')
        frame_java = os.path.join(self.broker_src, 'BrokerFrame.java')
        self.assertTrue(os.path.exists(server_java), "MqttBrokerServer.java missing")
        self.assertTrue(os.path.exists(frame_java), "BrokerFrame.java missing")

        os.makedirs(self.broker_bin, exist_ok=True)
        compile_cmd = ['javac', '-encoding', 'UTF-8', '-d', self.broker_bin, server_java, frame_java]
        compile_res = subprocess.run(compile_cmd, capture_output=True, text=True)
        self.assertEqual(compile_res.returncode, 0, f"Java Broker compilation failed: {compile_res.stderr}")

        tcp_port = 11883
        ws_port = 19001
        proc = subprocess.Popen(
            ['java', '-cp', self.broker_bin, 'com.iot.broker.MqttBrokerServer',
             '--console', '--tcp', str(tcp_port), '--ws', str(ws_port)],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        ws_client = None
        tcp_client = None
        try:
            _wait_for_port('127.0.0.1', tcp_port)
            _wait_for_port('127.0.0.1', ws_port)

            ws_connected = threading.Event()
            tcp_connected = threading.Event()
            ws_subscribed = threading.Event()
            ws_unsubscribed = threading.Event()
            ws_msgs = []
            tcp_msgs = []

            ws_client = _create_mqtt_client("test_ws_sub", transport="websockets", username="user1", password="123")
            tcp_client = _create_mqtt_client("test_tcp_pub", transport="tcp", username="user1", password="123")

            def on_ws_connect(client, userdata, flags, reason_code, properties=None):
                if getattr(reason_code, "value", reason_code) == 0:
                    ws_connected.set()

            def on_tcp_connect(client, userdata, flags, reason_code, properties=None):
                if getattr(reason_code, "value", reason_code) == 0:
                    tcp_connected.set()

            ws_client.on_connect = on_ws_connect
            ws_client.on_subscribe = lambda *args: ws_subscribed.set()
            ws_client.on_unsubscribe = lambda *args: ws_unsubscribed.set()
            ws_client.on_message = lambda c, u, m: ws_msgs.append((m.topic, m.payload.decode('utf-8'), m.retain))

            tcp_client.on_connect = on_tcp_connect
            tcp_client.on_message = lambda c, u, m: tcp_msgs.append((m.topic, m.payload.decode('utf-8')))

            ws_client.connect('127.0.0.1', ws_port, keepalive=30)
            tcp_client.connect('127.0.0.1', tcp_port, keepalive=30)
            ws_client.loop_start()
            tcp_client.loop_start()

            self.assertTrue(ws_connected.wait(timeout=4.0), "WebSocket client failed to connect to Java Broker")
            self.assertTrue(tcp_connected.wait(timeout=4.0), "TCP client failed to connect to Java Broker")

            # 1. Subscribe via WebSocket using + and # wildcards, plus TCP subscriber
            ws_client.subscribe([("sensor/#", 1), ("esp32/+/command", 1)])
            self.assertTrue(ws_subscribed.wait(timeout=3.0), "SUBACK not received on WebSocket")
            tcp_client.subscribe("esp32/led/command", qos=0)
            time.sleep(0.15)

            # 2. TCP publishes QoS 0 & QoS 1 messages + Retained status
            tcp_client.publish("sensor/temp", "29.5", qos=0).wait_for_publish(timeout=3.0)
            tcp_client.publish("esp32/led/command", "ON", qos=1).wait_for_publish(timeout=3.0)
            tcp_client.publish("esp32/status", "Online", qos=1, retain=True).wait_for_publish(timeout=3.0)

            deadline = time.time() + 3.0
            while time.time() < deadline and len(ws_msgs) < 2:
                time.sleep(0.05)

            self.assertIn(("sensor/temp", "29.5", False), ws_msgs)
            self.assertIn(("esp32/led/command", "ON", False), ws_msgs)

            # 3. Late subscribe receives retained message immediately
            ws_subscribed.clear()
            ws_client.subscribe("esp32/status", qos=1)
            self.assertTrue(ws_subscribed.wait(timeout=3.0))
            deadline = time.time() + 3.0
            while time.time() < deadline and not any(t == "esp32/status" for t, _, _ in ws_msgs):
                time.sleep(0.05)
            self.assertIn(("esp32/status", "Online", True), ws_msgs)

            # 4. WebSocket publishes QoS 1 back to TCP
            ws_client.publish("esp32/led/command", "OFF", qos=1).wait_for_publish(timeout=3.0)
            deadline = time.time() + 3.0
            while time.time() < deadline and ("esp32/led/command", "OFF") not in tcp_msgs:
                time.sleep(0.05)
            self.assertIn(("esp32/led/command", "OFF"), tcp_msgs)

            # 5. UNSUBSCRIBE stops further deliveries on sensor/#
            ws_client.unsubscribe("sensor/#")
            self.assertTrue(ws_unsubscribed.wait(timeout=3.0), "UNSUBACK not received on WebSocket")
            tcp_client.publish("sensor/temp", "99.9", qos=0).wait_for_publish(timeout=3.0)
            time.sleep(0.3)
            self.assertNotIn(("sensor/temp", "99.9", False), ws_msgs)
        finally:
            for c in (ws_client, tcp_client):
                if c is not None:
                    try:
                        c.loop_stop()
                        c.disconnect()
                    except Exception:
                        pass
            proc.terminate()
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill()
            finally:
                if proc.stdout is not None:
                    proc.stdout.close()

if __name__ == '__main__':
    unittest.main()
