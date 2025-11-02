# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import lifecycle as _lifecycle

sys.modules[__name__] = _lifecycle
