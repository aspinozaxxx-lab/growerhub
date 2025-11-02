# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import interfaces as _interfaces

sys.modules[__name__] = _interfaces
