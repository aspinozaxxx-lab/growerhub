# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt.handlers import ack as _ack

sys.modules[__name__] = _ack
