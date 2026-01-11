# ADR-001: исключение для композиции в адаптерах

Статус: принято

## Правило по умолчанию
1 handler = 1 facade operation (R-ADAPTER-01).

## Исключение
Только для композитных REST-ответов, когда нужно агрегировать данные из нескольких facade.
Адаптер в этих методах может вызывать несколько facade только для агрегации/маппинга ответа.

## Где разрешено (по факту в коде)
PlantController:
- GET /api/plants
- POST /api/plants
- GET /api/plants/{plant_id}
- PATCH /api/plants/{plant_id}

DevicesController:
- GET /api/devices
- GET /api/devices/my
- GET /api/admin/devices
- POST /api/admin/devices/{device_id}/assign
- POST /api/admin/devices/{device_id}/unassign

## Ограничения
- Адаптер не импортит *Entity/*Repository.
- Адаптер может вызывать несколько facade.
- Логика только агрегация/маппинг данных, без правил домена и без транзакций.
- Исключение действует только до появления нормальной orchestration (сейчас не внедряем).

## Как проверить
- нет импортов *.Entity/*.Repository в REST адаптерах;
- в указанных методах только facade-вызовы и mapping в REST DTO;
- нет @Transactional в адаптерах для этих методов;
- нет импортов *.internal.* из адаптеров.
