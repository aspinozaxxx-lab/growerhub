# ADR-001: исключение для композиции в адаптерах

Статус: принято

## Правило по умолчанию
Один обработчик вызывает одну публичную операцию Facade.

## Исключение
Только для композитных REST-ответов, когда нужно агрегировать данные из нескольких Facade.
Адаптер в этих методах может вызывать несколько Facade только для агрегации и маппинга ответа.

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
- Адаптер не импортирует *Entity/*Repository.
- Адаптер может вызывать несколько Facade.
- Логика только агрегация/маппинг данных, без правил домена и без транзакций.
- Исключение действует только до появления нормальной оркестрации.

## Как проверить
- нет импортов *.Entity/*.Repository в REST-адаптерах;
- в указанных методах только вызовы Facade и маппинг в REST DTO;
- нет @Transactional в адаптерах для этих методов;
- нет импортов *.internal.* из адаптеров.
