
"""Совместимость: прокладка для сервисных MQTT-подписчиков.

TODO: удалить модуль после полного перехода на service.mqtt.*.
"""

from __future__ import annotations

from service.mqtt.handlers.ack import (
    extract_device_id_from_ack_topic,
    make_ack_topic_filter,
)
from service.mqtt.handlers.device_state import (
    extract_device_id_from_state_topic,
    make_state_topic_filter,
)
from service.mqtt.lifecycle import (
    get_ack_subscriber,
    get_state_subscriber,
    init_ack_subscriber,
    init_state_subscriber,
    shutdown_ack_subscriber,
    shutdown_state_subscriber,
    start_ack_subscriber,
    start_state_subscriber,
    stop_ack_subscriber,
    stop_state_subscriber,
)
from service.mqtt.router import MqttAckSubscriber, MqttStateSubscriber

__all__ = [
    "MqttStateSubscriber",
    "MqttAckSubscriber",
    "make_state_topic_filter",
    "extract_device_id_from_state_topic",
    "make_ack_topic_filter",
    "extract_device_id_from_ack_topic",
    "init_state_subscriber",
    "get_state_subscriber",
    "start_state_subscriber",
    "stop_state_subscriber",
    "shutdown_state_subscriber",
    "init_ack_subscriber",
    "get_ack_subscriber",
    "start_ack_subscriber",
    "stop_ack_subscriber",
    "shutdown_ack_subscriber",
]
