"""Manual watering API endpoints."""

from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, conint

from mqtt_protocol import CmdPumpStart, CommandType
from mqtt_publisher import IMqttPublisher, get_publisher

router = APIRouter()


def get_mqtt_dep() -> IMqttPublisher:
    """Return configured MQTT publisher or raise 503."""

    try:
        return get_publisher()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


class ManualWateringStartIn(BaseModel):
    device_id: str
    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class ManualWateringStartOut(BaseModel):
    correlation_id: str


@router.post("/api/manual-watering/start", response_model=ManualWateringStartOut)
async def manual_watering_start(
    payload: ManualWateringStartIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
) -> ManualWateringStartOut:
    """Trigger manual watering by publishing a pump.start command."""

    correlation_id = uuid4().hex
    cmd = CmdPumpStart(
        type=CommandType.pump_start.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
        duration_s=payload.duration_s,
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStartOut(correlation_id=correlation_id)
