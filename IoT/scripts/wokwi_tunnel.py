#!/usr/bin/env python3
"""Open a free Pinggy SSH TCP tunnel so Wokwi ESP32 connects directly to local Java MQTT Broker."""

# shortcut: free.pinggy.io tunnels expire after 60m with a new port on restart; re-run this script before each demo or upgrade to LAN ESP32 / Pinggy Pro

import argparse
import os
import queue
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

PINGGY_HOST = "free.pinggy.io"
PINGGY_PORT = 443
PINGGY_USER = "tcp"
BROKER_TCP_PORT = 1883
BROKER_WS_PORT = 9001

ROOT_DIR = Path(__file__).resolve().parent.parent
WOKWI_DIR = ROOT_DIR / "wokwi"
SKETCH_PATH = WOKWI_DIR / "sketch.ino"

ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
TUNNEL_ADDR_RE = re.compile(r"tcp://([\w.\-]+):(\d+)")


def parse_tunnel_address(line: str) -> tuple[str, int] | None:
    """Extract (host, port) from a Pinggy tunnel output line, stripping ANSI escapes."""
    clean = ANSI_ESCAPE_RE.sub("", line)
    match = TUNNEL_ADDR_RE.search(clean)
    if not match:
        return None
    return match.group(1), int(match.group(2))


def update_sketch_broker(sketch_text: str, host: str, port: int) -> str:
    """Replace mqtt_server and mqtt_port declarations in sketch.ino."""
    updated, server_hits = re.subn(
        r'(const\s+char\s*\*\s*mqtt_server\s*=\s*)"[^"]*"(\s*;)',
        rf'\g<1>"{host}"\g<2>',
        sketch_text,
        count=1,
    )
    if server_hits != 1:
        raise ValueError("Could not find 'const char* mqtt_server = \"...\";' in sketch.ino")

    updated, port_hits = re.subn(
        r'(const\s+int\s+mqtt_port\s*=\s*)\d+(\s*;)',
        rf'\g<1>{port}\g<2>',
        updated,
        count=1,
    )
    if port_hits != 1:
        raise ValueError("Could not find 'const int mqtt_port = ...;' in sketch.ino")

    return updated


def is_port_open(host: str, port: int, timeout: float = 0.5) -> bool:
    """Probe whether a TCP port accepts connections."""
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def detect_java_broker_host(tcp_port: int = BROKER_TCP_PORT, ws_port: int = BROKER_WS_PORT) -> str | None:
    """Return local host bound by Java Broker, avoiding system Mosquitto on 127.0.0.1."""
    if is_port_open("127.0.0.2", tcp_port):
        return "127.0.0.2"
    if is_port_open("127.0.0.1", tcp_port) and is_port_open("127.0.0.1", ws_port):
        return "127.0.0.1"
    return None


def rebuild_wokwi_firmware() -> None:
    """Rebuild ESP32 firmware with PlatformIO so Wokwi loads updated broker settings."""
    print("Building Wokwi ESP32 firmware via PlatformIO...", flush=True)
    subprocess.run([sys.executable, "-m", "platformio", "run", "-d", str(WOKWI_DIR)], check=True)


def _stream_lines(pipe, out_queue: queue.Queue[str | None]) -> None:
    try:
        for line in iter(pipe.readline, ""):
            out_queue.put(line)
    finally:
        out_queue.put(None)


def _terminate_process(proc: subprocess.Popen[str]) -> None:
    if proc.poll() is not None:
        return
    proc.terminate()
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=3)


def main() -> int:
    parser = argparse.ArgumentParser(description="Tunnel local Java MQTT Broker via Pinggy SSH for Wokwi ESP32.")
    parser.add_argument("--reset-emqx", action="store_true", help="Restore broker.emqx.io:1883 in sketch.ino and rebuild.")
    args = parser.parse_args()

    if args.reset_emqx:
        text = SKETCH_PATH.read_text(encoding="utf-8")
        SKETCH_PATH.write_text(update_sketch_broker(text, "broker.emqx.io", 1883), encoding="utf-8")
        rebuild_wokwi_firmware()
        print("Restored sketch.ino to broker.emqx.io:1883 and rebuilt firmware.", flush=True)
        return 0

    local_host = detect_java_broker_host()
    if local_host is None:
        print(
            "LOI: Khong tim thay Java MQTT Broker dang chay (cong TCP 1883 / WS 9001).\n"
            "Vui long chay RUN_JAVA_BROKER.bat truoc roi chay lai script nay.",
            file=sys.stderr,
            flush=True,
        )
        return 1

    ssh_exe = shutil.which("ssh")
    if ssh_exe is None:
        print(
            "LOI: Khong tim thay OpenSSH client (ssh.exe) tren Windows.\n"
            "Vui long bat tinh nang 'OpenSSH Client' trong Windows Optional Features.",
            file=sys.stderr,
            flush=True,
        )
        return 1

    with tempfile.NamedTemporaryFile("w", suffix=".bat", delete=False, newline="\r\n", encoding="ascii") as tf:
        tf.write("@echo.\n")
        askpass_path = tf.name

    proc: subprocess.Popen[str] | None = None
    try:
        cmd = [
            ssh_exe,
            "-p", str(PINGGY_PORT),
            "-T",
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=NUL",
            "-o", "PubkeyAuthentication=no",
            "-o", "ConnectTimeout=10",
            "-o", "ServerAliveInterval=30",
            f"-R0:{local_host}:{BROKER_TCP_PORT}",
            f"{PINGGY_USER}@{PINGGY_HOST}",
        ]
        env = {
            **os.environ,
            "SSH_ASKPASS": askpass_path,
            "SSH_ASKPASS_REQUIRE": "force",
            "DISPLAY": "none",
        }
        proc = subprocess.Popen(
            cmd,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            env=env,
        )

        line_queue: queue.Queue[str | None] = queue.Queue()
        reader = threading.Thread(target=_stream_lines, args=(proc.stdout, line_queue), daemon=True)
        reader.start()

        captured: list[str] = []
        remote_addr: tuple[str, int] | None = None
        deadline = time.monotonic() + 20.0

        while time.monotonic() < deadline:
            remaining = max(0.05, deadline - time.monotonic())
            try:
                line = line_queue.get(timeout=min(0.25, remaining))
            except queue.Empty:
                if proc.poll() is not None and line_queue.empty():
                    break
                continue

            if line is None:
                break
            captured.append(line.rstrip("\r\n"))
            remote_addr = parse_tunnel_address(line)
            if remote_addr is not None:
                break

        if remote_addr is None:
            print("LOI: Khong mo duoc tunnel free.pinggy.io trong 20 giay.", file=sys.stderr, flush=True)
            if captured:
                print("\n".join(captured), file=sys.stderr, flush=True)
            return 1

        remote_host, remote_port = remote_addr
        sketch_text = SKETCH_PATH.read_text(encoding="utf-8")
        SKETCH_PATH.write_text(update_sketch_broker(sketch_text, remote_host, remote_port), encoding="utf-8")
        rebuild_wokwi_firmware()

        print("=" * 68, flush=True)
        print(f"TUNNEL SAN SANG: {remote_host}:{remote_port} -> Java Broker ({local_host}:{BROKER_TCP_PORT})", flush=True)
        print("Thoi han tunnel mien phi Pinggy: 60 phut (het han chi can chay lai file .bat nay).", flush=True)
        print("Firmware Wokwi da cap nhat & build thanh cong!", flush=True)
        print('Buoc 1: Tich "Bat buoc mat khau (user1 / 123)" tren Java Broker de bao mat.', flush=True)
        print("Buoc 2: Tren Java Client / Web, chon che do Local.", flush=True)
        print("Buoc 3: Trong VS Code Wokwi, bam Stop roi Start lai (hoac Restart) de ket noi.", flush=True)
        print("Giu cua so nay mo trong khi chay Wokwi. Nhan Ctrl+C de dung.", flush=True)
        print("=" * 68, flush=True)

        while True:
            try:
                line = line_queue.get(timeout=0.5)
            except queue.Empty:
                if proc.poll() is not None:
                    break
                continue
            if line is None:
                break
            print(line, end="", flush=True)
        return 0
    except KeyboardInterrupt:
        print("\nDang dong tunnel Pinggy SSH...", flush=True)
        return 0
    finally:
        if proc is not None:
            _terminate_process(proc)
        try:
            os.unlink(askpass_path)
        except OSError:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
