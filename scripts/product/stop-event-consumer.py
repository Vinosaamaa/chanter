"""Stop only the Linux consumer recorded by this checkout's product launcher."""
import os
from pathlib import Path
import signal
import sys


def stop(pid_file: Path, backend: Path, jar: str) -> None:
    pid = int(pid_file.read_text().strip())
    if pid <= 1:
        raise ValueError("Invalid consumer PID")
    # The descriptor pins the process identity, even if the PID is later reused.
    descriptor = os.pidfd_open(pid)
    try:
        process = Path(f"/proc/{pid}")
        expected_start = Path(str(pid_file) + ".start").read_text().strip()
        actual_start = (process / "stat").read_text().rsplit(")", 1)[1].split()[19]
        argv = (process / "cmdline").read_bytes().rstrip(b"\0").split(b"\0")
        if (not expected_start or expected_start != actual_start
                or (process / "cwd").resolve() != backend.resolve()
                or argv[1:] != [b"-jar", os.fsencode(jar)]):
            raise ValueError("Consumer ownership does not match launch receipt")
        signal.pidfd_send_signal(descriptor, signal.SIGTERM)
    finally:
        os.close(descriptor)


if __name__ == "__main__":
    stop(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3])
