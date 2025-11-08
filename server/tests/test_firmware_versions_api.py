import hashlib
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app, remount_firmware_static
from config import Settings, get_settings


def _override_settings(tmp_path: Path):
    settings = Settings(
        SERVER_PUBLIC_BASE_URL="https://example.com",
        FIRMWARE_BINARIES_DIR=str(tmp_path),
    )
    app.dependency_overrides[get_settings] = lambda: settings
    remount_firmware_static(settings)
    return settings


def _cleanup_overrides():
    app.dependency_overrides.pop(get_settings, None)
    remount_firmware_static()


def _write_firmware(path: Path, version: str, content: bytes, mtime: datetime) -> str:
    file_path = path / f"{version}.bin"
    file_path.write_bytes(content)
    ts = mtime.timestamp()
    os.utime(file_path, (ts, ts))
    return hashlib.sha256(content).hexdigest()


def test_firmware_versions_sorted_and_metadata(tmp_path):
    """Spisok vozvrashchaet metadannye i otsortirovan po mtime ubivayushche."""

    settings = _override_settings(tmp_path)
    try:
        newer_sha = _write_firmware(
            Path(settings.FIRMWARE_BINARIES_DIR),
            "2.3.4",
            b"newer",
            datetime.now(tz=timezone.utc),
        )
        older_sha = _write_firmware(
            Path(settings.FIRMWARE_BINARIES_DIR),
            "1.0.0",
            b"older",
            datetime.now(tz=timezone.utc) - timedelta(days=1),
        )

        with TestClient(app) as client:
            resp = client.get("/api/firmware/versions")

        assert resp.status_code == 200
        assert resp.headers.get("Cache-Control") == "no-store"
        data = resp.json()
        assert [item["version"] for item in data] == ["2.3.4", "1.0.0"]
        assert data[0]["sha256"] == newer_sha
        assert data[0]["size"] == len(b"newer")
        assert data[0]["mtime"].endswith("Z")
        assert data[1]["sha256"] == older_sha
    finally:
        _cleanup_overrides()


def test_firmware_versions_empty(tmp_path):
    """Pustoj katalog daet pustoj massiv bez oshibok."""

    _override_settings(tmp_path)
    try:
        with TestClient(app) as client:
            resp = client.get("/api/firmware/versions")
        assert resp.status_code == 200
        assert resp.json() == []
    finally:
        _cleanup_overrides()
