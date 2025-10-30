from datetime import datetime, timezone

import pytest

from service.mqtt.serialization import (
    Ack,
    AckResult,
    CmdPumpStart,
    CmdPumpStop,
    CommandType,
    DeviceState,
    ManualWateringState,
    ManualWateringStatus,
    serialize,
)
from service.mqtt.topics import ack_topic, cmd_topic, state_topic


def _loads(payload: bytes) -> str:
    return payload.decode("utf-8")


def test_topic_generation():
    device_id = "abc123"
    assert cmd_topic(device_id) == "gh/dev/abc123/cmd"
    assert ack_topic(device_id) == "gh/dev/abc123/ack"
    assert state_topic(device_id) == "gh/dev/abc123/state"


def test_serialize_deserialize_cmd_pump_start():
    ts = datetime(2025, 1, 1, 12, 0, tzinfo=timezone.utc)
    command = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id="corr-1",
        ts=ts,
        duration_s=20,
    )

    payload = serialize(command)
    restored = CmdPumpStart.model_validate_json(_loads(payload))

    assert isinstance(restored, CmdPumpStart)
    assert restored.type == CommandType.pump_start.value
    assert restored.correlation_id == command.correlation_id
    assert restored.duration_s == command.duration_s
    assert restored.ts == command.ts


def test_serialize_deserialize_cmd_pump_stop():
    ts = datetime(2025, 1, 1, 12, 0, tzinfo=timezone.utc)
    command = CmdPumpStop(
        type=CommandType.pump_stop.value,
        correlation_id="corr-2",
        ts=ts,
    )

    payload = serialize(command)
    restored = CmdPumpStop.model_validate_json(_loads(payload))

    assert isinstance(restored, CmdPumpStop)
    assert restored.correlation_id == command.correlation_id
    assert restored.ts == command.ts


def test_deserialize_cmd_invalid():
    with pytest.raises(ValueError):
        CommandType("unknown")


def test_serialize_deserialize_ack_success():
    ack = Ack(
        correlation_id="corr-1",
        result=AckResult.accepted,
        status=ManualWateringStatus.running,
        duration_s=20,
    )

    payload = serialize(ack)
    restored = Ack.model_validate_json(_loads(payload))

    assert restored.correlation_id == ack.correlation_id
    assert restored.result == ack.result
    assert restored.status == ack.status
    assert restored.duration_s == ack.duration_s
    assert restored.reason is None


def test_serialize_deserialize_ack_error_with_reason():
    ack = Ack(
        correlation_id="corr-2",
        result=AckResult.error,
        reason="pump jammed",
    )

    payload = serialize(ack)
    restored = Ack.model_validate_json(_loads(payload))

    assert restored.result == AckResult.error
    assert restored.reason == "pump jammed"


def test_serialize_deserialize_device_state_running():
    started = datetime(2025, 1, 1, 12, 0, tzinfo=timezone.utc)
    state = DeviceState(
        manual_watering=ManualWateringState(
            status=ManualWateringStatus.running,
            duration_s=30,
            started_at=started,
            remaining_s=10,
            correlation_id="corr-3",
        ),
        fw="1.2.3",
    )

    payload = serialize(state)
    restored = DeviceState.model_validate_json(_loads(payload))

    assert restored.manual_watering.status == ManualWateringStatus.running
    assert restored.manual_watering.duration_s == 30
    assert restored.manual_watering.started_at == started
    assert restored.manual_watering.remaining_s == 10
    assert restored.manual_watering.correlation_id == "corr-3"
    assert restored.fw == "1.2.3"


def test_serialize_deserialize_device_state_idle():
    state = DeviceState(
        manual_watering=ManualWateringState(status=ManualWateringStatus.idle),
    )

    payload = serialize(state)
    restored = DeviceState.model_validate_json(_loads(payload))

    assert restored.manual_watering.status == ManualWateringStatus.idle
    assert restored.manual_watering.duration_s is None
    assert restored.fw is None
