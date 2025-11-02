# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import store as _store

sys.modules[__name__] = _store
