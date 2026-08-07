import tempfile
import unittest
from pathlib import Path

from scripts.audit_public_content import inspect_https_public_url, inspect_record


class PublicContentAuditTest(unittest.TestCase):
    def test_public_https_is_allowed(self):
        self.assertEqual((True, "ok"), inspect_https_public_url("https://example.com/demo"))
        self.assertEqual([], inspect_record({
            "record_type": "WORK", "id": "1", "title": "普通作品", "summary": "介绍",
            "detail": "正文", "cover_url": "https://cdn.example.com/a.png",
            "demo_url": "https://example.com/demo", "video_url": "",
        }))

    def test_private_punycode_and_credentials_are_high_risk(self):
        for value in ("http://example.com", "https://127.0.0.1", "https://xn--example-9d0b.com", "https://u:p@example.com"):
            self.assertFalse(inspect_https_public_url(value)[0])
        findings = inspect_record({
            "record_type": "WORK", "id": "2", "title": "账号登录", "summary": "请输入密码验证",
            "detail": "访问 http://127.0.0.1/admin", "cover_url": "", "demo_url": "http://example.com", "video_url": "",
        })
        self.assertTrue(findings)
        self.assertTrue(all(item.severity == "HIGH" for item in findings))

    def test_missing_managed_upload_is_reported_without_modifying_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            findings = inspect_record({
                "record_type": "WORK", "id": "3", "title": "作品", "summary": "", "detail": "",
                "cover_url": "/uploads/missing.png", "demo_url": "", "video_url": "",
            }, root)
            self.assertEqual("missing_upload", findings[0].rule)
            self.assertFalse((root / "missing.png").exists())


if __name__ == "__main__":
    unittest.main()
