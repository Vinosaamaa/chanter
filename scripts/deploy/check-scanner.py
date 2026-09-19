"""Exercise the actual release scanner over its private socket on disposable CI."""
import datetime
import gzip
import locale
import struct
import subprocess
import sys

locale.setlocale(locale.LC_TIME, "C")
compose = ["docker", "compose", "--project-name", sys.argv[1], "-f", sys.argv[2]]


def command(payload):
    result = subprocess.run(compose + ["exec", "-T", "--user", "10001:10001", "clamav", "nc", "-w", "25", "-U", "/run/clamav/clamd.sock"],
                            input=payload, capture_output=True, timeout=35, check=True)
    return result.stdout.rstrip(b"\0\r\n")


def scan(payload):
    return command(b"zINSTREAM\0" + struct.pack("!I", len(payload)) + payload + b"\0\0\0\0")


def verify():
    if command(b"zPING\0") != b"PONG":
        raise RuntimeError("Scanner did not answer its private socket")
    version = command(b"zVERSION\0").decode("ascii")
    if not version.startswith("ClamAV 1.4.6/"):
        raise RuntimeError("Unexpected release scanner version")
    updated = datetime.datetime.strptime(version.split("/", 2)[2], "%a %b %d %H:%M:%S %Y").replace(tzinfo=datetime.timezone.utc)
    age = datetime.datetime.now(datetime.timezone.utc) - updated
    if not datetime.timedelta(minutes=-5) <= age <= datetime.timedelta(hours=72):
        raise RuntimeError("Release scanner signatures are stale")
    if scan(b"Clean course handout\n") != b"stream: OK":
        raise RuntimeError("Clean upload was rejected")
    # Standard harmless antivirus test signature, assembled to keep the test intent explicit.
    eicar = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + b"EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
    if b"FOUND" not in scan(eicar):
        raise RuntimeError("Antivirus test signature was accepted")
    expanded = gzip.compress(b"A" * (11 * 1024 * 1024))
    if b"FOUND" not in scan(expanded):
        raise RuntimeError("Archive exceeding inspection limit was accepted")


subprocess.run(compose + ["exec", "-T", "clamav", "sh", "-c",
    'test "$(id -u)" = 10002 && test "$(stat -c %a /var/lib/clamav)" = 700 '
    '&& test "$(stat -c %u:%g:%a /run/clamav)" = 10002:10001:2770 '
    '&& test "$(stat -c %a /run/clamav/clamd.sock)" = 660'], check=True)
outsider = subprocess.run(compose + ["exec", "-T", "--user", "10003:10003", "clamav", "nc", "-w", "3", "-U", "/run/clamav/clamd.sock"],
                          input=b"zPING\0", capture_output=True, timeout=10)
if outsider.returncode == 0 or b"PONG" in outsider.stdout:
    raise RuntimeError("Unrelated container identity can access the scanner socket")
verify()
subprocess.run(compose + ["restart", "clamav"], check=True)
subprocess.run(compose + ["up", "-d", "--no-deps", "--wait", "--wait-timeout", "600", "clamav"], check=True)
verify()
print("Release scanner passed clean, antivirus-test, archive-limit, permissions, freshness and restart checks")
