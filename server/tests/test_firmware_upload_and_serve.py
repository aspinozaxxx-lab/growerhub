import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.fastapi.routers.firmware import get_mqtt_dep
from app.main import app, remount_firmware_static
from config import Settings, get_settings


@pytest.fixture()
def client_with_fs(tmp_path):
    """Nastrojka app na vremennyj katalog firmware dlya testov."""

    settings = Settings(
        SERVER_PUBLIC_BASE_URL="https://example.com",
        FIRMWARE_BINARIES_DIR=str(tmp_path),
    )
    app.dependency_overrides[get_settings] = lambda: settings
    remount_firmware_static(settings)
    with TestClient(app) as client:
        yield client, settings
    app.dependency_overrides.pop(get_settings, None)
    remount_firmware_static()


@pytest.fixture(autouse=True)
def no_mqtt_publish(monkeypatch):
    """Zaglushka publishera, chtoby upload ne trebogal broker."""

    def _dummy_publisher():  # pragma: no cover - ne dolzhen vyzyvat'sya
        raise RuntimeError("MQTT should not be called in upload tests")

    app.dependency_overrides[get_mqtt_dep] = _dummy_publisher
    yield
    app.dependency_overrides.pop(get_mqtt_dep, None)


def test_upload_and_serve_file(client_with_fs):
    """Proveryaem uspeshnuyu zapis' i vozvrat statiki iz /firmware."""

    client, settings = client_with_fs
    version = "9.9.9"
    data = b"firmware-bytes"
    files = {"file": ("firmware.bin", data, "application/octet-stream")}

    resp = client.post("/api/upload-firmware", files=files, params={"version": version})
    assert resp.status_code == 201
    payload = resp.json()
    dst = Path(payload["path"])
    assert dst.exists()
    assert dst.read_bytes() == data

    resp_static = client.get(f"/firmware/{version}.bin")
    assert resp_static.status_code == 200
    assert resp_static.content == data


def test_upload_permission_error_returns_500(client_with_fs, monkeypatch):
    """Esli net prav zapisi, poluchaem 500 i soobshchenie permission denied."""

    client, settings = client_with_fs
    version = "1.1.1"
    target = Path(settings.FIRMWARE_BINARIES_DIR) / f"{version}.bin"
    original_open = Path.open

    def _deny_open(self, mode="r", *args, **kwargs):
        if self == target and "w" in mode:
            raise PermissionError("denied")
        return original_open(self, mode, *args, **kwargs)

    monkeypatch.setattr(Path, "open", _deny_open)

    files = {"file": ("firmware.bin", b"data", "application/octet-stream")}
    resp = client.post("/api/upload-firmware", files=files, params={"version": version})
    assert resp.status_code == 500
    assert resp.json()["detail"] == "permission denied"
    assert not target.exists()
