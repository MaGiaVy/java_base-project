import os
import json
import unittest

class TestBrokerConfig(unittest.TestCase):
    def setUp(self):
        self.config_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'mqtt_config.json')

    def test_config_file_exists(self):
        self.assertTrue(os.path.exists(self.config_path), f"File {self.config_path} does not exist.")

    def test_config_structure(self):
        if not os.path.exists(self.config_path):
            self.skipTest("Config file not found")

        with open(self.config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        # Check local broker config
        self.assertIn('local', data)
        self.assertEqual(data['local'].get('tcp_port'), 1883)
        self.assertEqual(data['local'].get('ws_port'), 9001)
        self.assertIn('username', data['local'])
        self.assertIn('password', data['local'])

        # Check public broker config
        self.assertIn('public_fallback', data)
        self.assertEqual(data['public_fallback'].get('broker_host'), 'broker.emqx.io')

        # Check topics
        self.assertIn('topics', data)
        topics = data['topics']
        self.assertEqual(topics.get('telemetry'), 'esp32/sensor/dht22/data')
        self.assertEqual(topics.get('legacy_telemetry'), 'sensor/data')
        self.assertEqual(topics.get('led_command'), 'esp32/led/command')
        self.assertEqual(topics.get('device_status'), 'esp32/status')

    def test_mosquitto_conf_exists(self):
        conf_path = os.path.join(os.path.dirname(__file__), '..', 'mosquitto', 'mosquitto.conf')
        self.assertTrue(os.path.exists(conf_path), f"File {conf_path} does not exist.")
        with open(conf_path, 'r', encoding='utf-8') as f:
            content = f.read()
        self.assertIn('listener 1883', content)
        self.assertIn('listener 9001', content)
        self.assertIn('protocol websockets', content)

if __name__ == '__main__':
    unittest.main()
