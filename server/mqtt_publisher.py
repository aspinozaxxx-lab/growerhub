"""Совместимость: временная прокладка к service.mqtt.publisher.

TODO: удалить после перевода всех импортов на service.mqtt.lifecycle.
"""

from __future__ import annotations

from service.mqtt.client import PahoMqttPublisher
from service.mqtt.interfaces import IMqttPublisher
from service.mqtt.lifecycle import get_publisher, init_publisher, shutdown_publisher

__all__ = [
    "IMqttPublisher",
    "PahoMqttPublisher",
    "init_publisher",
    "shutdown_publisher",
    "get_publisher",
]

