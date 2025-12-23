# Endpoints Checklist

Legend: DONE = implemented, TODO = stub (501 Not Implemented)

## Health
| Method | Path | Status |
| --- | --- | --- |
| GET | /health | DONE |

## Auth
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/auth/login | DONE |
| GET | /api/auth/sso/{provider}/login | DONE |
| GET | /api/auth/sso/{provider}/callback | DONE |
| GET | /api/auth/me | DONE |
| POST | /api/auth/refresh | DONE |
| POST | /api/auth/logout | DONE |
| PATCH | /api/auth/me | DONE |
| POST | /api/auth/change-password | DONE |
| GET | /api/auth/methods | DONE |
| POST | /api/auth/methods/local | DONE |
| DELETE | /api/auth/methods/{provider} | DONE |

## Users
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/users | DONE |
| GET | /api/users/{user_id} | DONE |
| POST | /api/users | DONE |
| PATCH | /api/users/{user_id} | DONE |
| DELETE | /api/users/{user_id} | DONE |

## Devices
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/device/{device_id}/status | DONE |
| GET | /api/device/{device_id}/settings | DONE |
| PUT | /api/device/{device_id}/settings | DONE |
| GET | /api/devices | DONE |
| GET | /api/devices/my | DONE |
| GET | /api/admin/devices | DONE |
| POST | /api/devices/assign-to-me | DONE |
| POST | /api/devices/{device_id}/unassign | DONE |
| POST | /api/admin/devices/{device_id}/assign | DONE |
| POST | /api/admin/devices/{device_id}/unassign | DONE |
| DELETE | /api/device/{device_id} | DONE |

## Manual Watering
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/manual-watering/start | DONE |
| POST | /api/manual-watering/stop | DONE |
| POST | /api/manual-watering/reboot | DONE |
| GET | /api/manual-watering/status | DONE |
| GET | /api/manual-watering/ack | DONE |
| GET | /api/manual-watering/wait-ack | DONE |
| GET | /_debug/manual-watering/config | DONE |
| POST | /_debug/shadow/state | DONE |
| GET | /_debug/manual-watering/snapshot | DONE |

## History
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/device/{device_id}/sensor-history | DONE |
| GET | /api/device/{device_id}/watering-logs | DONE |

## Firmware
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/device/{device_id}/firmware | DONE |
| POST | /api/upload-firmware | DONE |
| POST | /api/device/{device_id}/trigger-update | DONE |
| GET | /api/firmware/versions | DONE |

## Plants
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/plant-groups | TODO |
| POST | /api/plant-groups | TODO |
| PATCH | /api/plant-groups/{group_id} | TODO |
| DELETE | /api/plant-groups/{group_id} | TODO |
| GET | /api/plants | TODO |
| POST | /api/plants | TODO |
| GET | /api/plants/{plant_id} | TODO |
| PATCH | /api/plants/{plant_id} | TODO |
| DELETE | /api/plants/{plant_id} | TODO |
| POST | /api/plants/{plant_id}/devices/{device_id} | TODO |
| DELETE | /api/plants/{plant_id}/devices/{device_id} | TODO |
| GET | /api/plants/{plant_id}/journal | TODO |
| GET | /api/plants/{plant_id}/journal/export | TODO |
| POST | /api/plants/{plant_id}/journal | TODO |
| PATCH | /api/plants/{plant_id}/journal/{entry_id} | TODO |
| DELETE | /api/plants/{plant_id}/journal/{entry_id} | TODO |
| GET | /api/journal/photos/{photo_id} | TODO |
| GET | /api/admin/plants | TODO |
