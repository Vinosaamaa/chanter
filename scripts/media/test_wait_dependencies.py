"""Exercise readiness through its CLI, including optimized Python execution."""
import contextlib
import datetime
import io
import locale
import pathlib
import runpy
import unittest
from unittest.mock import MagicMock, patch


class ReadinessTest(unittest.TestCase):
    def run_readiness(self, version, scanner_only=True, status=200):
        connection = MagicMock()
        connection.__enter__.return_value = connection
        connection.recv.return_value = version.encode("ascii") + b"\0"
        response = MagicMock(status=status)
        response.__enter__.return_value = response
        arguments = ["wait-dependencies.py", "--timeout", "1"]
        if scanner_only:
            arguments.append("--scanner-only")
        with patch("sys.argv", arguments), patch("socket.AF_UNIX", 1, create=True), \
                patch("socket.socket", return_value=connection), \
                patch("urllib.request.urlopen", return_value=response), \
                patch("time.monotonic", side_effect=[0, 0, 0.9, 2]), patch("time.sleep"), \
                contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(pathlib.Path(__file__).with_name("wait-dependencies.py")), run_name="__main__")

    def test_stale_definitions_fail_even_with_python_optimization(self):
        with self.assertRaises(SystemExit):
            self.run_readiness("ClamAV 1.5.4/1/Sat Jan 1 00:00:00 2000")

    def test_unhealthy_object_store_fails_even_with_python_optimization(self):
        with self.assertRaises(SystemExit):
            self.run_readiness(self.fresh_version(), scanner_only=False, status=503)

    def test_fresh_definitions_pass(self):
        self.run_readiness(self.fresh_version())

    def test_readiness_selects_english_date_parsing(self):
        with patch("locale.setlocale", wraps=locale.setlocale) as setlocale:
            self.run_readiness(self.fresh_version())
        setlocale.assert_called_with(locale.LC_TIME, "C")

    @staticmethod
    def fresh_version():
        now = datetime.datetime.now(datetime.timezone.utc)
        return "ClamAV 1.5.4/1/" + now.strftime("%a %b %d %H:%M:%S %Y")


if __name__ == "__main__":
    unittest.main()
