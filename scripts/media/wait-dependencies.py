"""Bounded readiness for fresh ClamAV signatures and the optional isolated S3 test store."""
import argparse
import datetime
import os
import socket
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--scanner-only", action="store_true")
parser.add_argument("--timeout", type=int, default=600)
arguments = parser.parse_args()
if not 1 <= arguments.timeout <= 600:
    parser.error("timeout must be between 1 and 600 seconds")
deadline = time.monotonic() + arguments.timeout
last_report = None
last_version = None
while time.monotonic() < deadline:
    phase = "object-store readiness"
    try:
        if not arguments.scanner_only:
            with urllib.request.urlopen("http://127.0.0.1:9090/private-media-test", timeout=3) as response:
                assert response.status == 200
        phase = "scanner connection"
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(3)
            connection.connect(os.getenv("CHANTER_CLAMAV_SOCKET_PATH", "/run/clamav/clamd.sock"))
            connection.sendall(b"zVERSION\0")
            data = bytearray()
            while not data.endswith(b"\0") and len(data) < 4096:
                chunk = connection.recv(1024)
                if not chunk:
                    raise ValueError("scanner closed its readiness response")
                data.extend(chunk)
            phase = "scanner definition freshness"
            version = data.decode("ascii").strip("\0\r\n ")
            if version != last_version:
                # Bounded, escaped version metadata contains no application or scanned-file data.
                print("Scanner VERSION: " + repr(version[:256]), flush=True)
                last_version = version
            updated = datetime.datetime.strptime(version.split("/", 2)[2], "%a %b %d %H:%M:%S %Y").replace(tzinfo=datetime.timezone.utc)
            age = datetime.datetime.now(datetime.timezone.utc) - updated
            assert datetime.timedelta(minutes=-5) <= age <= datetime.timedelta(hours=72)
        print("ClamAV is ready; scanner definitions are within 72 hours")
        break
    except (OSError, ValueError, IndexError, AssertionError):
        if phase != last_report:
            print("Waiting for " + phase, flush=True)
            last_report = phase
        time.sleep(5)
else:
    raise SystemExit("Private media dependencies did not become ready with fresh scanner definitions")
