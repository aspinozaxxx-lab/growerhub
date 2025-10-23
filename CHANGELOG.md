# Changelog

� ���� ����� ����������� ��������� �������. ���������� ������ Keep a Changelog � Semantic Versioning.

## [2025-10-24]

### ���������
- �������� POST /api/manual-watering/stop � ����������� ������� pump.stop (server/api_manual_watering.py).
- ���� 	est_manual_watering_stop � �������� MQTT-���������� (server/tests/test_api_manual_watering_stop.py).
- ������� ��������� ��������� ��������� (server/device_shadow.py) � �������� emaining_s �� �������.
- �������� GET /api/manual-watering/status ��� ��������-���� �� ���������.
- ��������������� �������� POST /_debug/shadow/state ��� ��������� ������� � ������.
- ������� MQTT-��������� ��������� ��������� (server/mqtt_subscriber.py), ��������������� � ������ � ������� FastAPI.
- ���������������� AckStore � MQTT-��������� ������������� (server/ack_store.py, server/mqtt_subscriber.py).
- API GET /api/manual-watering/ack ��� ������������ ��������� ���������� �������.
- ����� ������������ state/ack � REST-���������� (server/tests/test_mqtt_subscriber_handler.py, server/tests/test_mqtt_ack_and_api.py).

### ��������
- ��������� ������� ����������� � ��������� � ������� MQTT-��������� � ������� ������ (server/mqtt_publisher.py, server/api_manual_watering.py).
- �������������� ����� ������� ������ ��������� ���������� ����������� (server/tests/test_api_manual_watering_start.py).
- ������ emaining_s ������ ��������� ����������� �� ������� �� duration_s � started_at.
- ������� ���� � AckStore ����������� �� ������ �������� MQTT-���������.

### ����������
- ���������� �������� /_debug/shadow/state ������� ��������� �� ��������� ���������� � ��������.

## [2025-10-23]

### ���������
- ��������� ������������ �������� � ������������ ������������ (firmware/config.ini, scripts/post_upload.py � ��.).

### ��������
- ��������� ��������� EEPROM � ��������� PlatformIO ��� ������ �� SPIFFS.

### ������ ���������
- ������� API Wi-Fi ��������� � ��������� ������������ SPIFFS.

