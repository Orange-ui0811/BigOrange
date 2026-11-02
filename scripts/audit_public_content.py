#!/usr/bin/env python3
"""Read-only audit of approved public content. It never fetches URLs or mutates data."""

from __future__ import annotations

import argparse
import csv
import ipaddress
import json
import os
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import urlparse

URL_PATTERN = re.compile(r"https?://[^\s<>'\"]+", re.IGNORECASE)
SHORTENER_HOSTS = {"bit.ly", "t.co", "tinyurl.com", "rebrand.ly", "cutt.ly", "dwz.cn"}
EXECUTABLE_SUFFIXES = (".exe", ".msi", ".apk", ".dmg", ".pkg", ".scr", ".bat", ".cmd", ".ps1")


@dataclass(frozen=True)
class Finding:
    severity: str
    record_type: str
    record_id: str
    field: str
    rule: str
    value: str
    recommendation: str


def inspect_https_public_url(value: str) -> tuple[bool, str]:
    if not value or len(value) > 2048:
        return False, "empty_or_too_long"
    try:
        parsed = urlparse(value.strip())
        host = (parsed.hostname or "").lower()
        if parsed.scheme.lower() != "https" or not host:
            return False, "https_required"
        if parsed.username or parsed.password:
            return False, "credentials_forbidden"
        if parsed.port not in (None, 443):
            return False, "non_standard_port"
        if host.startswith("xn--") or ".xn--" in host:
            return False, "punycode_forbidden"
        if host == "localhost" or host.endswith((".local", ".localhost", ".internal", ".lan", ".home")):
            return False, "local_host"
        try:
            address = ipaddress.ip_address(host)
            if not address.is_global:
                return False, "non_public_ip"
        except ValueError:
            if "." not in host or not re.fullmatch(r"[a-z0-9.-]+", host):
                return False, "invalid_host"
        return True, "ok"
    except (ValueError, UnicodeError):
        return False, "invalid_url"


def inspect_record(record: dict[str, str], uploads_root: Path | None = None) -> list[Finding]:
    findings: list[Finding] = []
    record_type, record_id = record["record_type"], record["id"]

    def add(severity: str, field: str, rule: str, value: str, recommendation: str) -> None:
        findings.append(Finding(severity, record_type, record_id, field, rule, value[:500], recommendation))

    for field in ("cover_url", "demo_url", "video_url"):
        value = (record.get(field) or "").strip()
        if not value:
            continue
        if value.startswith(("/uploads/", "/api/media/")):
            if uploads_root and value.startswith("/uploads/"):
                local_path = (uploads_root / value.removeprefix("/uploads/")).resolve()
                try:
                    local_path.relative_to(uploads_root.resolve())
                except ValueError:
                    add("HIGH", field, "upload_path_escape", value, "取消公开并核对上传记录")
                    continue
                if not local_path.is_file():
                    add("HIGH", field, "missing_upload", value, "恢复文件或取消公开")
            continue
        safe, reason = inspect_https_public_url(value)
        if not safe:
            add("HIGH", field, f"unsafe_external_url:{reason}", value, "取消公开或替换为安全 HTTPS 公网地址")
            continue
        parsed = urlparse(value)
        if parsed.hostname in SHORTENER_HOSTS:
            add("MEDIUM", field, "shortened_url", value, "人工确认最终目标域名")
        if parsed.path.lower().endswith(EXECUTABLE_SUFFIXES):
            add("HIGH", field, "executable_download", value, "取消公开并核对是否存在诱导下载")

    text_fields = {key: record.get(key, "") or "" for key in ("title", "summary", "detail")}
    combined = "\n".join(text_fields.values()).lower()
    credential_terms = ("密码", "验证码", "助记词", "私钥", "password", "verification code", "seed phrase")
    login_terms = ("登录", "登陆", "login", "sign in", "账号验证")
    if any(term in combined for term in credential_terms) and any(term in combined for term in login_terms):
        add("HIGH", "content", "credential_collection_language", combined, "人工核对是否存在仿冒登录或凭据收集")
    if any(term in combined for term in ("关闭杀毒", "关闭安全软件", "绕过安全警告", "disable antivirus")):
        add("HIGH", "content", "security_bypass_language", combined, "取消公开并人工复核")

    for field, text in text_fields.items():
        for url in URL_PATTERN.findall(text):
            safe, reason = inspect_https_public_url(url.rstrip(".,;，。；)）"))
            if not safe:
                add("HIGH", field, f"unsafe_embedded_url:{reason}", url, "清理正文外链后重新审核")
            elif (urlparse(url).hostname or "").lower() in SHORTENER_HOSTS:
                add("MEDIUM", field, "embedded_shortened_url", url, "人工确认最终目标域名")
    return findings


def decode_hex(value: str) -> str:
    try:
        return bytes.fromhex(value).decode("utf-8", errors="replace") if value else ""
    except ValueError:
        return ""


def load_records(args: argparse.Namespace) -> list[dict[str, str]]:
    query = """
SELECT 'WORK', CAST(id AS CHAR), HEX(COALESCE(title,'')), HEX(COALESCE(summary,'')),
       HEX(COALESCE(detail,'')), HEX(COALESCE(cover_url,'')), HEX(COALESCE(demo_url,'')), HEX(COALESCE(video_url,''))
FROM work WHERE audit_status='APPROVED' AND visibility_status='PUBLIC'
UNION ALL
SELECT 'REQUIREMENT', CAST(id AS CHAR), HEX(COALESCE(title,'')), '', HEX(COALESCE(detail,'')), '', '', ''
FROM requirement WHERE audit_status='APPROVED' AND visibility_status='PUBLIC';
""".strip()
    command = [args.mysql_binary, "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4",
               "-h", args.host, "-P", str(args.port), "-u", args.user, args.database, "-e", query]
    environment = os.environ.copy()
    if args.password_env and os.environ.get(args.password_env):
        environment["MYSQL_PWD"] = os.environ[args.password_env]
    completed = subprocess.run(command, check=True, capture_output=True, text=True, encoding="utf-8", env=environment)
    records = []
    fields = ("record_type", "id", "title", "summary", "detail", "cover_url", "demo_url", "video_url")
    for line in completed.stdout.splitlines():
        columns = line.split("\t")
        if len(columns) != len(fields):
            raise RuntimeError("Unexpected mysql audit output")
        record = dict(zip(fields, columns))
        for field in fields[2:]:
            record[field] = decode_hex(record[field])
        records.append(record)
    return records


def write_reports(findings: list[Finding], json_path: Path, csv_path: Path, record_count: int) -> None:
    json_path.parent.mkdir(parents=True, exist_ok=True)
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"readOnly": True, "recordsScanned": record_count, "findings": [asdict(item) for item in findings]}
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(Finding.__dataclass_fields__))
        writer.writeheader()
        writer.writerows(asdict(item) for item in findings)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Read-only audit of approved public OrangeVC content")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--database", default=os.getenv("MYSQL_DB", "orangejd"))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "orangejd_app"))
    parser.add_argument("--password-env", default="MYSQL_PASSWORD")
    parser.add_argument("--mysql-binary", default="mysql")
    parser.add_argument("--uploads-root", type=Path, default=Path(os.getenv("UPLOAD_PATH", "/data/orangejd/uploads")))
    parser.add_argument("--json-output", type=Path, default=Path("build/security/public-content-audit.json"))
    parser.add_argument("--csv-output", type=Path, default=Path("build/security/public-content-audit.csv"))
    parser.add_argument("--fail-on-high", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    records = load_records(args)
    findings = [finding for record in records for finding in inspect_record(record, args.uploads_root)]
    write_reports(findings, args.json_output, args.csv_output, len(records))
    high_count = sum(item.severity == "HIGH" for item in findings)
    print(f"Scanned {len(records)} public records; findings={len(findings)}, high={high_count}")
    print(f"JSON: {args.json_output}\nCSV: {args.csv_output}")
    return 2 if args.fail_on_high and high_count else 0


if __name__ == "__main__":
    sys.exit(main())
