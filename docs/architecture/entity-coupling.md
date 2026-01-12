Zachem etot dokument:
- usilivayem ponimanie, kak ORM-svyazi prolamyvayut mezhdomennoe razdelenie.
- fiksiruem, chto izmeneniya v odnih entity odrazhayutsya v neskol'kih domenah.
- eto fakticheskaya karta bez rekomendatsiy.

## Karta svyazey
- DeviceEntity (device) -> UserEntity (user) : `@ManyToOne device.user`
- PumpEntity (pump) -> DeviceEntity (device) : `@ManyToOne pump.device`
- PumpPlantBindingEntity (pump) -> PumpEntity (pump) : `@ManyToOne binding.pump`
- PumpPlantBindingEntity (pump) -> PlantEntity (plant) : `@ManyToOne binding.plant`
- SensorEntity (sensor) -> DeviceEntity (device) : `@ManyToOne sensor.device`
- SensorPlantBindingEntity (sensor) -> SensorEntity (sensor) : `@ManyToOne binding.sensor`
- SensorPlantBindingEntity (sensor) -> PlantEntity (plant) : `@ManyToOne binding.plant`
- PlantEntity (plant) -> UserEntity (user) : `@ManyToOne plant.user`
- PlantEntity (plant) -> PlantGroupEntity (plant) : `@ManyToOne plant.plantGroup`
- PlantGroupEntity (plant) -> UserEntity (user) : `@ManyToOne group.user`
- PlantJournalEntryEntity (journal) -> PlantEntity (plant) : `@ManyToOne entry.plant`
- PlantJournalEntryEntity (journal) -> UserEntity (user) : `@ManyToOne entry.user`
- PlantJournalEntryEntity (journal) -> PlantJournalWateringDetailsEntity (journal) : `@OneToOne entry.wateringDetails`
- PlantJournalPhotoEntity (journal) -> PlantJournalEntryEntity (journal) : `@ManyToOne photo.journalEntry`

## Osi mira
### UserEntity
- DeviceEntity.user
- PlantEntity.user
- PlantGroupEntity.user
- PlantJournalEntryEntity.user

### PlantEntity
- PlantEntity.plantGroup
- PumpPlantBindingEntity.plant
- SensorPlantBindingEntity.plant
- PlantJournalEntryEntity.plant

### DeviceEntity
- PumpEntity.device
- SensorEntity.device

## Pochemu eto vazhno dlya refaktoringa
- lyuboe izmenenie UserEntity tyanet device/plant/plantGroup/journal domeny.
- PlantEntity svyazan s pump/sensor binding tablicami i zhurnalami, chto dilit granicy.
- DeviceEntity formiruet ORM grapf s pump/sensor/plant, chto mojet privesti k cyklam pri redeploy.
- PumpPlantBinding/SensorPlantBinding uchityvayut odnogo i togo zhe PlantEntity v tol'ko podkladochke.
- PlantJournalEntry zaveshchen na PlantEntity i UserEntity v odnom klasse, chto usilivaet obshchie transakcii.

## Istochniki
- backend/src/main/java/ru/growerhub/backend/device/DeviceEntity.java
- backend/src/main/java/ru/growerhub/backend/pump/PumpEntity.java
- backend/src/main/java/ru/growerhub/backend/pump/PumpPlantBindingEntity.java
- backend/src/main/java/ru/growerhub/backend/sensor/SensorEntity.java
- backend/src/main/java/ru/growerhub/backend/sensor/SensorPlantBindingEntity.java
- backend/src/main/java/ru/growerhub/backend/plant/PlantEntity.java
- backend/src/main/java/ru/growerhub/backend/plant/PlantGroupEntity.java
- backend/src/main/java/ru/growerhub/backend/journal/PlantJournalEntryEntity.java
- backend/src/main/java/ru/growerhub/backend/journal/PlantJournalWateringDetailsEntity.java
- backend/src/main/java/ru/growerhub/backend/journal/PlantJournalPhotoEntity.java
- backend/src/main/java/ru/growerhub/backend/user/UserEntity.java

