# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import handlers as _handlers

sys.modules[__name__] = _handlers
