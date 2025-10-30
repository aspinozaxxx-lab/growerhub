from datetime import datetime, timedelta

from fastapi.testclient import TestClient

from app.main import app
from service.mqtt.handlers.ack import extract_device_id_from_ack_topic
from service.mqtt.router import MqttAckSubscriber
from service.mqtt.serialization import Ack, AckResult, ManualWateringStatus
from service.mqtt.store import AckStore, get_ack_store, init_ack_store, shutdown_ack_store


def test_extract_device_id_from_ack_topic_valid():
    """????????? ?????????? ?????????? device_id ?? ACK-??????."""

    assert extract_device_id_from_ack_topic("gh/dev/xyz/ack") == "xyz"


def test_extract_device_id_from_ack_topic_invalid():
    """???????? ?????? ?????? ?????????????? ???????????."""

    assert extract_device_id_from_ack_topic("gh/dev//ack") is None
    assert extract_device_id_from_ack_topic("gh/dev/xyz/state") is None
    assert extract_device_id_from_ack_topic("bad/topic") is None


class FakeAckMessage:
    """??????????? ???????? MQTT-????????? ??? ???????????? on_message."""

    def __init__(self, topic: str, payload: bytes) -> None:
        self.topic = topic
        self.payload = payload


def _make_ack_payload(correlation_id: str, result: AckResult, status: ManualWateringStatus | None = None) -> bytes:
    """????????? JSON ??? ACK ??? ??, ??? ??? ???????? ??????????."""

    ack = Ack(
        correlation_id=correlation_id,
        result=result,
        status=status,
    )
    return ack.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")


def test_ack_subscriber_on_message_stores_ack():
    """????????? ?????? ????????? ?????????? ACK ? ????."""

    store = AckStore()
    subscriber = MqttAckSubscriber(store, client_factory=lambda: None)

    payload = _make_ack_payload("corr-1", AckResult.accepted, ManualWateringStatus.running)
    message = FakeAckMessage("gh/dev/abc123/ack", payload)

    subscriber._on_message(None, None, message)  # type: ignore[attr-defined]

    ack = store.get("corr-1")
    assert ack is not None
    assert ack.result == AckResult.accepted
    assert ack.status == ManualWateringStatus.running


def test_ack_api_returns_data_when_present():
    """REST-???????? ?????? ???????? ACK, ???? ?? ??? ?????? ? ???????."""

    with TestClient(app) as client:
        # ??????????? correlation_id -> 404
        response = client.get("/api/manual-watering/ack", params={"correlation_id": "missing"})
        assert response.status_code == 404

        # ????? ACK ? ???? ? ????????? JSON-?????
        store = get_ack_store()
        ack = Ack(
            correlation_id="corr-2",
            result=AckResult.rejected,
            reason="pump jammed",
            status=ManualWateringStatus.idle,
        )
        store.put("abc123", ack)

        response = client.get("/api/manual-watering/ack", params={"correlation_id": "corr-2"})
        assert response.status_code == 200
        data = response.json()
        assert data["correlation_id"] == "corr-2"
        assert data["result"] == AckResult.rejected.value
        assert data["reason"] == "pump jammed"
        assert data["status"] == ManualWateringStatus.idle.value

        # ???????, ????? ?? ?????? ?? ?????? ?????.
        store.cleanup(max_age_seconds=0)


def test_ack_store_cleanup_removes_old_entries():
    """cleanup ??????? ?????????? ACK ? ?????????? ?????????? ????????? ???????."""

    store = AckStore()
    recent_ack = Ack(correlation_id="recent", result=AckResult.accepted)
    old_ack = Ack(correlation_id="old", result=AckResult.error, reason="timeout")

    store.put("dev", recent_ack)
    store.put("dev", old_ack)

    # ????????????? ?????? ?????? ??????
    threshold = datetime.utcnow() - timedelta(seconds=10)
    store._storage["old"] = (  # type: ignore[attr-defined]
        store._storage["old"][0],  # type: ignore[attr-defined]
        store._storage["old"][1],  # type: ignore[attr-defined]
        threshold - timedelta(seconds=1),
    )

    removed = store.cleanup(max_age_seconds=5)
    assert removed == 1
    assert store.get("old") is None
    assert store.get("recent") is not None

