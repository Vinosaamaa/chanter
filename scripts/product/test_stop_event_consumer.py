"""Negative ownership checks use a real child; the product drill covers Java stop/start."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("stop_consumer", Path(__file__).with_name("stop-event-consumer.py"))
consumer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(consumer)


@unittest.skipUnless(sys.platform == "linux" and hasattr(os, "pidfd_open"), "Linux product launcher")
class ConsumerOwnershipTest(unittest.TestCase):
    def test_missing_stale_or_wrong_command_receipts_never_signal_a_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(30)"], cwd=root)
            try:
                pid_file = root / "consumer.pid"
                pid_file.write_text(str(child.pid))
                receipt = Path(str(pid_file) + ".start")
                actual = Path(f"/proc/{child.pid}/stat").read_text().rsplit(")", 1)[1].split()[19]
                for start in [None, str(int(actual) - 1), actual]:
                    with self.subTest(start=start):
                        if start is not None:
                            receipt.write_text(start)
                        with self.assertRaises((ValueError, FileNotFoundError)):
                            consumer.stop(pid_file, root, "search-service.jar")
                        self.assertIsNone(child.poll(), "unrelated child must remain alive")
            finally:
                child.terminate()
                child.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
