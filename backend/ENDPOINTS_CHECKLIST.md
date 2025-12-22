# Endpoints Checklist

Legend: DONE = implemented, TODO = stub (501 Not Implemented)

## Health
| Method | Path | Status |
| --- | --- | --- |
| GET | /health | DONE |

## Auth
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/auth/login | TODO |
| GET | /api/auth/sso/{provider}/login | TODO |
| GET | /api/auth/sso/{provider}/callback | TODO |
| GET | /api/auth/me | DONE |
| POST | /api/auth/refresh | TODO |
| POST | /api/auth/logout | TODO |
| PATCH | /api/auth/me | TODO |
| POST | /api/auth/change-password | TODO |
| GET | /api/auth/methods | TODO |
| POST | /api/auth/methods/local | TODO |
| DELETE | /api/auth/methods/{provider} | TODO |

## Users
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/users | TODO |
| GET | /api/users/{user_id} | TODO |
| POST | /api/users | TODO |
| PATCH | /api/users/{user_id} | TODO |
| DELETE | /api/users/{user_id} | TODO |

## Devices
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/device/{device_id}/status | TODO |
| GET | /api/device/{device_id}/settings | TODO |
| PUT | /api/device/{device_id}/settings | TODO |
| GET | /api/devices | TODO |
| GET | /api/devices/my | TODO |
| GET | /api/admin/devices | TODO |
| POST | /api/devices/assign-to-me | TODO |
| POST | /api/devices/{device_id}/unassign | TODO |
| POST | /api/admin/devices/{device_id}/assign | TODO |
| POST | /api/admin/devices/{device_id}/unassign | TODO |
| DELETE | /api/device/{device_id} | TODO |

## Manual Watering
| Method | Path | Status |
| --- | --- | --- |
| POST | /api/manual-watering/start | TODO |
| POST | /api/manual-watering/stop | TODO |
| POST | /api/manual-watering/reboot | TODO |
| GET | /api/manual-watering/status | TODO |
| GET | /api/manual-watering/ack | TODO |
| GET | /api/manual-watering/wait-ack | TODO |
| GET | /_debug/manual-watering/config | TODO |
| POST | /_debug/shadow/state | TODO |
| GET | /_debug/manual-watering/snapshot | TODO |

## History
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/device/{device_id}/sensor-history | TODO |
| GET | /api/device/{device_id}/watering-logs | TODO |

## Firmware
| Method | Path | Status |
| --- | --- | --- |
| GET | /api/device/{device_id}/firmware | TODO |
| POST | /api/upload-firmware | TODO |
| POST | /api/device/{device_id}/trigger-update | TODO |
| GET | /api/firmware/versions | TODO |

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
