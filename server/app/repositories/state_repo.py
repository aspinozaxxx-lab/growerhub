from __future__ import annotations

import json
from contextlib import contextmanager
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

from sqlalchemy import select, delete
from sqlalchemy.orm import Session

from app.core.database import SessionLocal
from app.models.database_models import DeviceStateLastDB, MqttAckDB


@contextmanager
def _session_scope() -> Session:
    """Translitem: upravlyaem sessionoj v odnom meste dlya rollback/commit."""

    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


class DeviceStateLastRepository:
    """Translitem: rabota s tablicej poslednih sostoyanii bez izmeneniya API."""

    def upsert_state(self, device_id: str, state: Dict[str, Any], updated_at: datetime) -> None:
        """Translitem: upsert po device_id chtoby vosstanovit ten posle restarta."""

        payload = json.dumps(state, ensure_ascii=False)
        with _session_scope() as session:
            record = session.execute(
                select(DeviceStateLastDB).where(DeviceStateLastDB.device_id == device_id)
            ).scalar_one_or_none()
            if record:
                record.state_json = payload
                record.updated_at = updated_at
            else:
                session.add(
                    DeviceStateLastDB(
                        device_id=device_id,
                        state_json=payload,
                        updated_at=updated_at,
                    )
                )

    def get_state(self, device_id: str) -> Optional[Dict[str, Any]]:
        """Translitem: vozvrashaem dict state esli zapis est."""

        with _session_scope() as session:
            record = session.execute(
                select(DeviceStateLastDB).where(DeviceStateLastDB.device_id == device_id)
            ).scalar_one_or_none()
            if not record:
                return None
            return {
                "device_id": record.device_id,
                "state": json.loads(record.state_json),
                "updated_at": record.updated_at,
            }

    def touch(self, device_id: str, now_utc: datetime | None = None) -> None:
        """Translitem: obnovlyaem tol'ko updated_at kak puls zhizni, sozdaem zaglushku esli zapisi net."""

        instant = now_utc or datetime.utcnow()
        with _session_scope() as session:
            record = session.execute(
                select(DeviceStateLastDB).where(DeviceStateLastDB.device_id == device_id)
            ).scalar_one_or_none()
            if record:
                # Translitem: pri nalichii zapisi tol'ko perestavlyaem updated_at.
                record.updated_at = instant
            else:
                # Translitem: sozdaem minimal'nyj state chtoby otmetit poslednee MQTT-sobytie.
                session.add(
                    DeviceStateLastDB(
                        device_id=device_id,
                        state_json="{}",
                        updated_at=instant,
                    )
                )

    def list_states(self, device_ids: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        """Translitem: vozvrashaem spisok sostoyanii, filtr po device_ids esli nuzhen."""

        with _session_scope() as session:
            stmt = select(DeviceStateLastDB)
            if device_ids:
                stmt = stmt.where(DeviceStateLastDB.device_id.in_(device_ids))
            records = session.execute(stmt).scalars().all()
            return [
                {
                    "device_id": rec.device_id,
                    "state": json.loads(rec.state_json),
                    "updated_at": rec.updated_at,
                }
                for rec in records
            ]


class MqttAckRepository:
    """Translitem: hranim MQTT ACK s TTL bez vmeshatelstva v API logiku."""

    def put_ack(
        self,
        correlation_id: str,
        device_id: str,
        ack_dict: Dict[str, Any],
        received_at: datetime,
        ttl_seconds: Optional[int],
    ) -> None:
        """Translitem: sohranyaem ack i rasschityvaem expires_at esli nato."""

        expires_at = None
        if ttl_seconds is not None:
            expires_at = received_at + timedelta(seconds=ttl_seconds)
        payload = json.dumps(ack_dict, ensure_ascii=False)
        result_value = str(ack_dict.get("result")) if ack_dict.get("result") is not None else ""
        status_raw = ack_dict.get("status")
        status_value = str(status_raw) if status_raw is not None else None

        with _session_scope() as session:
            record = session.execute(
                select(MqttAckDB).where(MqttAckDB.correlation_id == correlation_id)
            ).scalar_one_or_none()
            if record:
                record.device_id = device_id
                record.result = result_value
                record.status = status_value
                record.payload_json = payload
                record.received_at = received_at
                record.expires_at = expires_at
            else:
                session.add(
                    MqttAckDB(
                        correlation_id=correlation_id,
                        device_id=device_id,
                        result=result_value,
                        status=status_value,
                        payload_json=payload,
                        received_at=received_at,
                        expires_at=expires_at,
                    )
                )

    def get_ack(self, correlation_id: str) -> Optional[Dict[str, Any]]:
        """Translitem: ignoriruem prosrochennye ack po expires_at."""

        now = datetime.utcnow()
        with _session_scope() as session:
            record = session.execute(
                select(MqttAckDB).where(MqttAckDB.correlation_id == correlation_id)
            ).scalar_one_or_none()
            if not record:
                return None
            if record.expires_at is not None and record.expires_at <= now:
                return None
            return {
                "correlation_id": record.correlation_id,
                "device_id": record.device_id,
                "result": record.result,
                "status": record.status,
                "payload": json.loads(record.payload_json),
                "received_at": record.received_at,
                "expires_at": record.expires_at,
            }

    def cleanup_expired(self, now: datetime) -> int:
        """Translitem: udalyaem ack s expires_at <= now, vozvrashaem skolko udalili."""

        with _session_scope() as session:
            result = session.execute(
                delete(MqttAckDB).where(
                    MqttAckDB.expires_at.is_not(None),
                    MqttAckDB.expires_at <= now,
                )
            )
            return result.rowcount or 0
