# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import serialization as _serialization

sys.modules[__name__] = _serialization
