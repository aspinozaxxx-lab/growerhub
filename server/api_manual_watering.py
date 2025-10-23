"""REST API для ручного управления насосом: команды pump.start и pump.stop."""

from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, conint

from mqtt_protocol import CmdPumpStart, CmdPumpStop, CommandType
from mqtt_publisher import IMqttPublisher, get_publisher

router = APIRouter()


def get_mqtt_dep() -> IMqttPublisher:
    """Возвращает готовый MQTT-паблишер или выдаёт 503, если он недоступен."""

    try:
        return get_publisher()
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="MQTT publisher unavailable") from exc


class ManualWateringStartIn(BaseModel):
    """Тело запроса для запуска полива.

    device_id: идентификатор устройства, которому нужно отправить команду.
    duration_s: желаемая длительность ручного полива в секундах.
    """

    device_id: str
    duration_s: conint(ge=1, le=3600)  # type: ignore[valid-type]


class ManualWateringStartOut(BaseModel):
    """Ответ при запуске полива.

    correlation_id: уникальный идентификатор команды,
    по которому устройство пришлёт подтверждение (ack) и обновит состояние.
    """

    correlation_id: str


class ManualWateringStopIn(BaseModel):
    """Тело запроса для остановки полива.

    device_id: идентификатор устройства, которому требуется отправить pump.stop.
    """

    device_id: str


class ManualWateringStopOut(BaseModel):
    """Ответ при остановке полива, аналогичен старту.

    correlation_id: уникальный идентификатор отправленной команды pump.stop.
    """

    correlation_id: str


@router.post("/api/manual-watering/start", response_model=ManualWateringStartOut)
async def manual_watering_start(
    payload: ManualWateringStartIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
) -> ManualWateringStartOut:
    """Запускает ручной полив, публикуя pump.start.

    Корреляционный идентификатор генерируем на сервере, чтобы фронтенд/оператор
    могли отслеживать подтверждения и состояние. На фронт возвращаем только
    correlation_id: все остальные поля доступны через MQTT-сообщения и REST-метрики.
    """

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


@router.post("/api/manual-watering/stop", response_model=ManualWateringStopOut)
async def manual_watering_stop(
    payload: ManualWateringStopIn,
    publisher: IMqttPublisher = Depends(get_mqtt_dep),
) -> ManualWateringStopOut:
    """Останавливает ручной полив, публикуя pump.stop.

    Корреляционный идентификатор генерируется сервером по той же причине, что
    и для pump.start — единая связка команд и ответов. На выходе только id,
    дальше оператор сверяет подтверждения по MQTT.
    """

    correlation_id = uuid4().hex
    cmd = CmdPumpStop(
        type=CommandType.pump_stop.value,
        correlation_id=correlation_id,
        ts=datetime.utcnow(),
    )

    try:
        publisher.publish_cmd(payload.device_id, cmd)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to publish manual watering command") from exc

    return ManualWateringStopOut(correlation_id=correlation_id)
