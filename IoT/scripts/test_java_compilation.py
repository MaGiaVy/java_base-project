import os
import subprocess
import unittest

class TestJavaCompilation(unittest.TestCase):
    def setUp(self):
        self.java_dir = os.path.join(os.path.dirname(__file__), '..', 'java-client')
        self.lib_dir = os.path.join(self.java_dir, 'lib')
        self.src_dir = os.path.join(self.java_dir, 'src', 'com', 'iot')
        self.jar_path = os.path.join(self.lib_dir, 'org.eclipse.paho.client.mqttv3-1.2.5.jar')

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

if __name__ == '__main__':
    unittest.main()
