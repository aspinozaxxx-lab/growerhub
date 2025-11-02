# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import router as _router

sys.modules[__name__] = _router
