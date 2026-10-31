import os
import json
import unittest

class TestFirmwareSyntax(unittest.TestCase):
    def setUp(self):
        self.sketch_path = os.path.join(os.path.dirname(__file__), '..', 'wokwi', 'sketch.ino')
        self.diagram_path = os.path.join(os.path.dirname(__file__), '..', 'wokwi', 'diagram.json')
        self.libraries_path = os.path.join(os.path.dirname(__file__), '..', 'wokwi', 'libraries.txt')

    def test_wokwi_files_exist(self):
        self.assertTrue(os.path.exists(self.sketch_path), f"File {self.sketch_path} does not exist.")
        self.assertTrue(os.path.exists(self.diagram_path), f"File {self.diagram_path} does not exist.")
        self.assertTrue(os.path.exists(self.libraries_path), f"File {self.libraries_path} does not exist.")

    def test_diagram_components(self):
        if not os.path.exists(self.diagram_path):
            self.skipTest("diagram.json not found")
        with open(self.diagram_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        part_types = [p.get('type') for p in data.get('parts', [])]
        self.assertIn('board-esp32-devkit-c-v4', part_types)
        self.assertIn('wokwi-dht22', part_types)
        self.assertIn('wokwi-led', part_types)
        self.assertIn('wokwi-resistor', part_types)

    def test_sketch_firmware_logic(self):
        if not os.path.exists(self.sketch_path):
            self.skipTest("sketch.ino not found")
        with open(self.sketch_path, 'r', encoding='utf-8') as f:
            code = f.read()

        # Check required pin defines
        self.assertIn('DHTPIN', code)
        self.assertIn('16', code)
        self.assertIn('LEDPIN', code)
        self.assertIn('4', code)

        # Check topics
        self.assertIn('esp32/sensor/dht22/data', code)
        self.assertIn('esp32/led/command', code)
        self.assertIn('esp32/status', code)

        # Check Wi-Fi SSID
        self.assertIn('Wokwi-GUEST', code)

        # Check LED control logic ON/OFF
        self.assertIn('"ON"', code)
        self.assertIn('"OFF"', code)
        self.assertIn('digitalWrite', code)

        # Check JSON formatting
        self.assertIn('temperature', code)
        self.assertIn('humidity', code)

if __name__ == '__main__':
    unittest.main()
