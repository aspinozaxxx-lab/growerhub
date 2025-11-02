# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt.handlers import device_state as _device_state

sys.modules[__name__] = _device_state
