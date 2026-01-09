# ADR-001: adapter composition exception

Status: prinjato

## Pravilo po umolchaniju
1 handler = 1 facade operation (R-ADAPTER-01).

## Iskljuchenie
Tolko dlya kompozitnyh REST-otvetov, kogda nuzhno agregirovat' dannye iz neskol'kih facade.
Adapter v etih metodah mozet vyzyvat' neskol'ko facade tol'ko dlya agregacii/maapinga otveta.

## Gde razresheno (po faktu v kode)
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

## Ogranichenija
- Adapter ne importit *Entity/*Repository.
- Adapter mozet vyzyvat' neskol'ko facade.
- Logika tol'ko agregacija/maaping dannyh, bez pravil domena i bez tranzakcij.
- Iskljuchenie dejstvuet tol'ko do pojavlenija normal'noj orchestration (sejchas ne vnedrjaem).

## Kak proverit'
- net importov *.Entity/*.Repository v REST adapterah;
- v ukazannyh metodah tol'ko facade-vyzovy i mapping v REST DTO;
- net @Transactional v adapterah dlya etih metodov;
- net importov *.internal.* iz adapterov.
