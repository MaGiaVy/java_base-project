import unittest

try:
    from scripts import wokwi_tunnel
except ImportError:
    import wokwi_tunnel


SAMPLE_SKETCH = """// Config
const char* ssid = "Wokwi-GUEST";
const char* mqtt_server = "broker.emqx.io";
const int mqtt_port = 1883;
const char* mqtt_user = "user1";
"""


class TestWokwiTunnel(unittest.TestCase):
    def test_parse_tunnel_address_matches_valid_lines(self):
        self.assertEqual(
            wokwi_tunnel.parse_tunnel_address("tcp://abdkc-115-77-68-71.run.pinggy-free.link:44135\n"),
            ("abdkc-115-77-68-71.run.pinggy-free.link", 44135),
        )
        self.assertEqual(
            wokwi_tunnel.parse_tunnel_address("\x1b[32mForwarding:\x1b[0m tcp://my-host.a.pinggy.link:9999"),
            ("my-host.a.pinggy.link", 9999),
        )

    def test_parse_tunnel_address_returns_none_for_unrelated_lines(self):
        self.assertIsNone(wokwi_tunnel.parse_tunnel_address("Allocated port 4 for remote forward to 127.0.0.2:1883"))
        self.assertIsNone(wokwi_tunnel.parse_tunnel_address(""))

    def test_update_sketch_broker_replaces_server_and_port(self):
        updated = wokwi_tunnel.update_sketch_broker(SAMPLE_SKETCH, "abdkc-115-77-68-71.run.pinggy-free.link", 44135)
        self.assertIn('const char* mqtt_server = "abdkc-115-77-68-71.run.pinggy-free.link";', updated)
        self.assertIn("const int mqtt_port = 44135;", updated)
        self.assertIn('const char* ssid = "Wokwi-GUEST";', updated)
        self.assertIn('const char* mqtt_user = "user1";', updated)

    def test_update_sketch_broker_raises_when_missing(self):
        with self.assertRaises(ValueError):
            wokwi_tunnel.update_sketch_broker("const int mqtt_port = 1883;", "host", 44135)
        with self.assertRaises(ValueError):
            wokwi_tunnel.update_sketch_broker('const char* mqtt_server = "x";', "host", 44135)


if __name__ == "__main__":
    unittest.main()
