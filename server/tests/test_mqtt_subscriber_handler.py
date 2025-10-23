import json
from datetime import datetime, timedelta, timezone

from mqtt_protocol import DeviceState, ManualWateringState, ManualWateringStatus
from device_shadow import DeviceShadowStore
from mqtt_subscriber import (
    MqttStateSubscriber,
    extract_device_id_from_state_topic,
)


def test_extract_device_id_from_state_topic_valid():
    """Корректный топик возвращает device_id."""

    assert extract_device_id_from_state_topic("gh/dev/abc123/state") == "abc123"


def test_extract_device_id_from_state_topic_invalid():
    """Некорректные топики дают None, чтобы обработчик их игнорировал."""

    assert extract_device_id_from_state_topic("gh/dev//state") is None
    assert extract_device_id_from_state_topic("gh/dev/abc123/status") is None
    assert extract_device_id_from_state_topic("bad/topic") is None


class FakeMessage:
    """Минимальная заглушка MQTT-сообщения для вызова on_message."""

    def __init__(self, topic: str, payload: bytes) -> None:
        self.topic = topic
        self.payload = payload


def _make_state_payload(status: ManualWateringStatus, duration: int, started_at: datetime, correlation_id: str) -> bytes:
    """Формируем JSON payload так же, как его отправит устройство."""

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
    """При получении валидного state стор должен обновиться."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)  # фабрика не используется в тесте

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
    """Невалидный JSON игнорируется и стор не меняется."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)

    message = FakeMessage("gh/dev/abc123/state", b"{invalid")
    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    assert store.get_last_state("abc123") is None


def test_on_message_wrong_topic_ignored():
    """Сообщение из другого топика не должно менять стор."""

    store = DeviceShadowStore()
    subscriber = MqttStateSubscriber(store, client_factory=lambda: None)

    state = DeviceState(manual_watering=ManualWateringState(status=ManualWateringStatus.idle))
    message = FakeMessage("gh/dev/abc123/status", state.model_dump_json().encode("utf-8"))
    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    assert store.get_last_state("abc123") is None
