import os
import unittest

class TestWebAssets(unittest.TestCase):
    def setUp(self):
        self.html_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'index.html')
        self.css_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'styles.css')
        self.js_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'app.js')

    def test_web_files_exist(self):
        self.assertTrue(os.path.exists(self.html_path), f"File {self.html_path} does not exist.")
        self.assertTrue(os.path.exists(self.css_path), f"File {self.css_path} does not exist.")
        self.assertTrue(os.path.exists(self.js_path), f"File {self.js_path} does not exist.")

    def test_html_structure(self):
        if not os.path.exists(self.html_path):
            self.skipTest("index.html not found")
        with open(self.html_path, 'r', encoding='utf-8') as f:
            html = f.read()

        # Check required element IDs
        required_ids = [
            'tempGauge', 'humGauge', 'tempValue', 'humValue',
            'ledToggle', 'connectionStatus', 'tempChart', 'logTable'
        ]
        for elem_id in required_ids:
            self.assertIn(f'id="{elem_id}"', html, f"Missing id: {elem_id}")

        # Check MQTT library script
        self.assertIn('mqtt', html.lower())

    def test_js_logic(self):
        if not os.path.exists(self.js_path):
            self.skipTest("app.js not found")
        with open(self.js_path, 'r', encoding='utf-8') as f:
            js = f.read()

        # Check topic subscriptions and publishing
        self.assertIn('esp32/sensor/dht22/data', js)
        self.assertIn('esp32/led/command', js)
        self.assertTrue('\"ON\"' in js or "'ON'" in js)
        self.assertTrue('\"OFF\"' in js or "'OFF'" in js)

        # Check gauge drawing function
        self.assertIn('drawGauge', js)

if __name__ == '__main__':
    unittest.main()
