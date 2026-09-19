"""Check the seed's real JSON selection against processing and failed resources."""
import contextlib
import io
import json
import pathlib
import unittest
from unittest.mock import patch


class DemoResourceTest(unittest.TestCase):
    def select(self, resources):
        script = pathlib.Path(__file__).parents[1].joinpath("seed-workable-product-demo.sh").read_text()
        selector = script.split('RESOURCE_ID=$(echo "$EXISTING_RESOURCES" | python3 -c "', 1)[1].split('\n")', 1)[0]
        output = io.StringIO()
        with patch("sys.stdin", io.StringIO(json.dumps({"courseResources": resources}))), contextlib.redirect_stdout(output):
            exec(selector.replace("$RESOURCE_TITLE", "Guide"), {})
        return output.getvalue().strip()

    def test_failed_or_rejected_seed_is_not_reused(self):
        self.assertEqual(self.select([
            {"id": "failed", "title": "Guide", "status": "FAILED", "aiApproved": True},
            {"id": "rejected", "title": "Guide", "status": "REJECTED", "aiApproved": True},
            {"id": "safe", "title": "Guide", "status": "AVAILABLE", "aiApproved": True},
        ]), "safe")

    def test_pending_seed_is_reused_while_its_scan_finishes(self):
        self.assertEqual(self.select([{"id": "pending", "title": "Guide", "status": "PROCESSING", "aiApproved": True}]), "pending")

    def test_resource_without_ai_approval_is_not_reused(self):
        self.assertEqual(self.select([{"id": "private", "title": "Guide", "status": "AVAILABLE", "aiApproved": False}]), "")


if __name__ == "__main__":
    unittest.main()
