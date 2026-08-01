#!/usr/bin/env python3
"""Seriynaya podgotovka Grovika bez vyvoda MQTT-parolya."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request


DEVICE_ID_PATTERN = re.compile(r"^GROVIKA_[0-9A-F]{6}$")
PASSWORD_PATTERN = re.compile(r"^[A-Za-z0-9_-]{43}$")
MAC_PATTERN = re.compile(r"MAC:\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Podgotovit i proshit seriynuyu Grovika")
    parser.add_argument("--port", required=True, help="Posledovatelnyi port, naprimer COM10")
    parser.add_argument("--api-base-url", default="https://growerhub.ru")
    parser.add_argument("--rotate", action="store_true", help="Yavno perevypustit credential")
    return parser.parse_args()


def platformio_command() -> list[str]:
    return [sys.executable, "-m", "platformio"]


def subprocess_environment() -> dict[str, str]:
    environment = os.environ.copy()
    environment.pop("GH_FACTORY_ADMIN_TOKEN", None)
    return environment


def platformio_core_dir() -> Path:
    result = subprocess.run(
        platformio_command() + ["system", "info", "--json-output"],
        check=True,
        capture_output=True,
        text=True,
        env=subprocess_environment(),
    )
    info = json.loads(result.stdout)
    return Path(info["core_dir"]["value"])


def read_board(port: str) -> tuple[str, str]:
    esptool = platformio_core_dir() / "packages" / "tool-esptoolpy" / "esptool.py"
    if not esptool.is_file():
        raise RuntimeError("esptool.py ne naiden v PlatformIO Core")
    result = subprocess.run(
        [sys.executable, str(esptool), "--port", port, "read_mac"],
        check=True,
        capture_output=True,
        text=True,
        env=subprocess_environment(),
    )
    output = result.stdout + result.stderr
    matches = MAC_PATTERN.findall(output)
    if not matches:
        raise RuntimeError("ne udalos schitat MAC ustroistva")
    mac = matches[-1].upper()

    if "Chip is ESP32-C3" in output:
        environment = "esp32c3_supermini"
    elif "Chip is ESP32-D0WD" in output or "Chip is ESP32-PICO" in output:
        environment = "esp32dev"
    else:
        raise RuntimeError("podklyuchennyi chip ne podderzhivaetsya etoi proshivkoi")
    return mac, environment


def device_id_from_mac(mac: str) -> str:
    octets = mac.split(":")
    if len(octets) != 6:
        raise RuntimeError("nekorrektnyi MAC")
    return "GROVIKA_" + "".join(octets[-3:])


def provision(api_base_url: str, token: str, device_id: str, rotate: bool) -> dict:
    parsed_base_url = urllib.parse.urlparse(api_base_url)
    if parsed_base_url.scheme != "https" or not parsed_base_url.hostname:
        raise RuntimeError("factory API dolzhen ispolzovat HTTPS")
    url = api_base_url.rstrip("/") + "/api/admin/devices/provision"
    payload = json.dumps({"device_id": device_id, "rotate": rotate}).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                raise RuntimeError(f"provision API vernul HTTP {response.status}")
            if "no-store" not in response.headers.get("Cache-Control", ""):
                raise RuntimeError("provision API ne zapretil keshirovanie sekreta")
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"provision API vernul HTTP {error.code}") from error


def validate_credential(value: dict, expected_device_id: str) -> dict:
    device_id = value.get("device_id")
    password = value.get("password")
    if device_id != expected_device_id or not DEVICE_ID_PATTERN.fullmatch(str(device_id)):
        raise RuntimeError("provision API vernul drugoi device_id")
    if value.get("username") != device_id or value.get("client_id") != device_id:
        raise RuntimeError("provision API vernul nekorrektnyi MQTT identity")
    if value.get("host") != "growerhub.ru" or value.get("port") != 8883 or value.get("tls") is not True:
        raise RuntimeError("provision API vernul nekorrektnyi MQTTS endpoint")
    if not isinstance(password, str) or not PASSWORD_PATTERN.fullmatch(password):
        raise RuntimeError("provision API vernul nekorrektnyi parol")
    return {"schema_version": 1, "device_id": device_id, "password": password}


def broker_smoke(host: str, port: int, device_id: str, password: str) -> None:
    try:
        import paho.mqtt.client as mqtt
    except ImportError as error:
        raise RuntimeError("ustanovite factory dependencies iz requirements-factory.txt") from error

    def connect(secret: str):
        connected = threading.Event()
        result: list[object] = []
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=device_id,
            protocol=mqtt.MQTTv5,
        )
        client.username_pw_set(device_id, secret)
        client.tls_set()

        def on_connect(_client, _userdata, _flags, reason_code, _properties):
            result.append(reason_code)
            connected.set()

        client.on_connect = on_connect
        client.connect(host, port, keepalive=15)
        client.loop_start()
        if not connected.wait(15):
            client.loop_stop()
            raise RuntimeError("MQTTS smoke ne poluchil CONNACK")
        return client, result[0]

    wrong_password = password[:-1] + ("A" if password[-1] != "A" else "B")
    wrong_client, wrong_reason = connect(wrong_password)
    try:
        if not wrong_reason.is_failure:
            raise RuntimeError("MQTTS prinjal nevernyi parol")
    finally:
        wrong_client.disconnect()
        wrong_client.loop_stop()

    client, connect_reason = connect(password)
    if connect_reason.is_failure:
        client.disconnect()
        client.loop_stop()
        raise RuntimeError("MQTTS otklonil vypushchennye credentials")

    publish_event = threading.Event()
    publish_results: dict[int, object] = {}
    disconnect_event = threading.Event()
    disconnect_results: list[object] = []

    def on_publish(_client, _userdata, message_id, reason_code, _properties):
        publish_results[message_id] = reason_code
        publish_event.set()

    def on_disconnect(_client, _userdata, _flags, reason_code, _properties):
        disconnect_results.append(reason_code)
        disconnect_event.set()

    client.on_publish = on_publish
    client.on_disconnect = on_disconnect
    foreign_device_id = "GROVIKA_FFFFFF" if device_id != "GROVIKA_FFFFFF" else "GROVIKA_000000"
    try:
        own_publish = client.publish(
            f"gh/dev/{device_id}/factory-smoke",
            payload=b'{"ok":true}',
            qos=1,
            retain=False,
        )
        if own_publish.rc != mqtt.MQTT_ERR_SUCCESS or not publish_event.wait(15):
            raise RuntimeError("MQTTS smoke ne podtverdil publikaciyu v svoi namespace")
        if publish_results[own_publish.mid].is_failure:
            raise RuntimeError("MQTTS zapretil publikaciyu v svoi namespace")

        publish_event.clear()
        foreign_publish = client.publish(
            f"gh/dev/{foreign_device_id}/factory-smoke",
            payload=b'{"ok":true}',
            qos=1,
            retain=False,
        )
        if foreign_publish.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError("MQTTS smoke ne podtverdil ACL otvet na chuzhuyu publikaciyu")
        deadline = time.monotonic() + 15
        while not publish_event.is_set() and not disconnect_event.is_set() and time.monotonic() < deadline:
            time.sleep(0.05)
        publish_reason = publish_results.get(foreign_publish.mid)
        disconnected_as_unauthorized = bool(
            disconnect_results and getattr(disconnect_results[-1], "value", None) == 135
        )
        if publish_reason is None and not disconnected_as_unauthorized:
            raise RuntimeError("MQTTS smoke ne podtverdil ACL otvet na chuzhuyu publikaciyu")
        if publish_reason is not None and not publish_reason.is_failure:
            raise RuntimeError("MQTTS razreshil publikaciyu v chuzhoi namespace")
    finally:
        client.disconnect()
        client.loop_stop()

    subscribe_client, subscribe_connect_reason = connect(password)
    if subscribe_connect_reason.is_failure:
        subscribe_client.disconnect()
        subscribe_client.loop_stop()
        raise RuntimeError("MQTTS otklonil credentials pered proverkoj podpisky")
    subscribe_event = threading.Event()
    subscribe_results: dict[int, list[object]] = {}

    def on_subscribe(_client, _userdata, message_id, reason_codes, _properties):
        subscribe_results[message_id] = reason_codes
        subscribe_event.set()

    subscribe_client.on_subscribe = on_subscribe
    try:
        subscribe_rc, subscribe_mid = subscribe_client.subscribe(f"gh/dev/{foreign_device_id}/#", qos=1)
        if subscribe_rc != mqtt.MQTT_ERR_SUCCESS or subscribe_mid is None or not subscribe_event.wait(15):
            raise RuntimeError("MQTTS smoke ne podtverdil ACL otvet na chuzhuyu podpisku")
        if not subscribe_results[subscribe_mid] or not all(
            reason.is_failure for reason in subscribe_results[subscribe_mid]
        ):
            raise RuntimeError("MQTTS razreshil podpisku na chuzhoi namespace")
    finally:
        subscribe_client.disconnect()
        subscribe_client.loop_stop()


def remove_secret(path: Path) -> None:
    if not path.exists():
        return
    try:
        path.write_bytes(b"0" * path.stat().st_size)
    finally:
        path.unlink(missing_ok=True)


def remove_secret_artifacts(firmware_dir: Path, environment: str) -> None:
    build_dir = firmware_dir / ".pio" / "build" / environment
    for filename in ("littlefs.bin", "spiffs.bin", "fatfs.bin"):
        remove_secret(build_dir / filename)


def flash(firmware_dir: Path, environment: str, port: str) -> None:
    base = platformio_command() + ["run", "-e", environment, "--upload-port", port]
    child_environment = subprocess_environment()
    subprocess.run(
        base + ["-t", "upload"],
        cwd=firmware_dir,
        check=True,
        env=child_environment,
        stdout=sys.stderr,
        stderr=sys.stderr,
    )
    subprocess.run(
        base + ["-t", "uploadfs"],
        cwd=firmware_dir,
        check=True,
        env=child_environment,
        stdout=sys.stderr,
        stderr=sys.stderr,
    )


def main() -> int:
    args = parse_args()
    token = os.environ.get("GH_FACTORY_ADMIN_TOKEN", "").strip()
    if not token:
        raise RuntimeError("zadaite GH_FACTORY_ADMIN_TOKEN v okruzhenii")

    firmware_dir = Path(__file__).resolve().parents[1]
    secret_path = firmware_dir / "data" / "cfg" / "mqtt.json"
    remove_secret(secret_path)
    for known_environment in ("esp32dev", "esp32c3_supermini"):
        remove_secret_artifacts(firmware_dir, known_environment)
    mac, environment = read_board(args.port)
    device_id = device_id_from_mac(mac)
    if not DEVICE_ID_PATTERN.fullmatch(device_id):
        raise RuntimeError("ne udalos postroit device_id")

    credential: dict = {}
    mqtt_config: dict = {}
    try:
        credential = provision(args.api_base_url, token, device_id, args.rotate)
        mqtt_config = validate_credential(credential, device_id)
        broker_smoke(
            credential["host"],
            credential["port"],
            device_id,
            mqtt_config["password"],
        )
        secret_path.parent.mkdir(parents=True, exist_ok=True)
        secret_path.write_text(
            json.dumps(mqtt_config, separators=(",", ":")),
            encoding="utf-8",
        )
        flash(firmware_dir, environment, args.port)
    finally:
        if "password" in mqtt_config:
            mqtt_config["password"] = ""
        if "password" in credential:
            credential["password"] = ""
        remove_secret(secret_path)
        remove_secret_artifacts(firmware_dir, environment)

    print(device_id)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"factory error: {error}", file=sys.stderr)
        raise SystemExit(1)
