import json
from datetime import datetime, timedelta, timezone

from service.mqtt.handlers.device_state import extract_device_id_from_state_topic
from service.mqtt.router import MqttStateSubscriber
from service.mqtt.serialization import DeviceState, ManualWateringState, ManualWateringStatus
from service.mqtt.store import DeviceShadowStore


def test_extract_device_id_from_state_topic_valid():
    """╨Ъ╨╛╤А╤А╨╡╨║╤В╨╜╤Л╨╣ ╤В╨╛╨┐╨╕╨║ ╨▓╨╛╨╖╨▓╤А╨░╤Й╨░╨╡╤В device_id."""

    assert extract_device_id_from_state_topic("gh/dev/abc123/state") == "abc123"


def test_extract_device_id_from_state_topic_invalid():
    """╨Э╨╡╨║╨╛╤А╤А╨╡╨║╤В╨╜╤Л╨╡ ╤В╨╛╨┐╨╕╨║╨╕ ╨┤╨░╤О╤В None, ╤З╤В╨╛╨▒╤Л ╨╛╨▒╤А╨░╨▒╨╛╤В╤З╨╕╨║ ╨╕╤Е ╨╕╨│╨╜╨╛╤А╨╕╤А╨╛╨▓╨░╨╗."""

    assert extract_device_id_from_state_topic("gh/dev//state") is None
    assert extract_device_id_from_state_topic("gh/dev/abc123/status") is None
    assert extract_device_id_from_state_topic("bad/topic") is None


class FakeMessage:
    """╨Ь╨╕╨╜╨╕╨╝╨░╨╗╤М╨╜╨░╤П ╨╖╨░╨│╨╗╤Г╤И╨║╨░ MQTT-╤Б╨╛╨╛╨▒╤Й╨╡╨╜╨╕╤П ╨┤╨╗╤П ╨▓╤Л╨╖╨╛╨▓╨░ on_message."""

    def __init__(self, topic: str, payload: bytes) -> None:
        self.topic = topic
        self.payload = payload


def _make_state_payload(status: ManualWateringStatus, duration: int, started_at: datetime, correlation_id: str) -> bytes:
    """╨д╨╛╤А╨╝╨╕╤А╤Г╨╡╨╝ JSON payload ╤В╨░╨║ ╨╢╨╡, ╨║╨░╨║ ╨╡╨│╨╛ ╨╛╤В╨┐╤А╨░╨▓╨╕╤В ╤Г╤Б╤В╤А╨╛╨╣╤Б╤В╨▓╨╛."""

    state = DeviceState(
        manual_watering=ManualWateringState(
            status=status,
            duration_s=duration,
            started_at=started_at,
            correlation_id=correlation_id,
        )
    )
    return state.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")


def test_on_message_updates_store_running_state():
    """╨Я╤А╨╕ ╨┐╨╛╨╗╤Г╤З╨╡╨╜╨╕╨╕ ╨▓╨░╨╗╨╕╨┤╨╜╨╛╨│╨╛ state ╤Б╤В╨╛╤А ╨┤╨╛╨╗╨╢╨╡╨╜ ╨╛╨▒╨╜╨╛╨▓╨╕╤В╤М╤Б╤П."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)  # ╤Д╨░╨▒╤А╨╕╨║╨░ ╨╜╨╡ ╨╕╤Б╨┐╨╛╨╗╤М╨╖╤Г╨╡╤В╤Б╤П ╨▓ ╤В╨╡╤Б╤В╨╡

    started_at = datetime.now(timezone.utc) - timedelta(seconds=5)
    payload = _make_state_payload(ManualWateringStatus.running, 20, started_at, "corr-1")

    message = FakeMessage("gh/dev/abc123/state", payload)
    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    view = store.get_manual_watering_view("abc123", now=datetime.now(timezone.utc))
    assert view is not None
    assert view["status"] == "running"
    assert view["duration_s"] == 20
    assert view["correlation_id"] == "corr-1"


def test_on_message_invalid_json_does_not_crash():
    """╨Э╨╡╨▓╨░╨╗╨╕╨┤╨╜╤Л╨╣ JSON ╨╕╨│╨╜╨╛╤А╨╕╤А╤Г╨╡╤В╤Б╤П ╨╕ ╤Б╤В╨╛╤А ╨╜╨╡ ╨╝╨╡╨╜╤П╨╡╤В╤Б╤П."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)

    message = FakeMessage("gh/dev/abc123/state", b"{invalid")
    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    assert store.get_last_state("abc123") is None


def test_on_message_wrong_topic_ignored():
    """╨б╨╛╨╛╨▒╤Й╨╡╨╜╨╕╨╡ ╨╕╨╖ ╨┤╤А╤Г╨│╨╛╨│╨╛ ╤В╨╛╨┐╨╕╨║╨░ ╨╜╨╡ ╨┤╨╛╨╗╨╢╨╜╨╛ ╨╝╨╡╨╜╤П╤В╤М ╤Б╤В╨╛╤А."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)

    state = DeviceState(manual_watering=ManualWateringState(status=ManualWateringStatus.idle))
    message = FakeMessage("gh/dev/abc123/status", state.model_dump_json().encode("utf-8"))
    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    assert store.get_last_state("abc123") is None

