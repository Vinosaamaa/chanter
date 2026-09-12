"""Bounded readiness check for the isolated test stack, including fresh ClamAV signatures."""
import datetime
import socket
import time
import urllib.request

deadline = time.monotonic() + 600
last_report = None
last_version = None
while time.monotonic() < deadline:
    phase = "object-store readiness"
    try:
        with urllib.request.urlopen("http://127.0.0.1:9090/private-media-test", timeout=3) as response:
            assert response.status == 200
        phase = "scanner connection"
        with socket.create_connection(("127.0.0.1", 3310), timeout=3) as connection:
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
                # Isolated public CI fixture: bounded, escaped scanner metadata contains no application data.
                print("Scanner VERSION: " + repr(version[:256]), flush=True)
                last_version = version
            updated = datetime.datetime.strptime(version.split("/", 2)[2], "%a %b %d %H:%M:%S %Y").replace(tzinfo=datetime.timezone.utc)
            age = datetime.datetime.now(datetime.timezone.utc) - updated
            assert datetime.timedelta(minutes=-5) <= age <= datetime.timedelta(hours=72)
        print("S3 emulator and ClamAV are ready; scanner definitions are within 72 hours")
        break
    except (OSError, ValueError, IndexError, AssertionError):
        if phase != last_report:
            print("Waiting for " + phase, flush=True)
            last_report = phase
        time.sleep(5)
else:
    raise SystemExit("Private media dependencies did not become ready with fresh scanner definitions")
