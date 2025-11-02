# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import topics as _topics

sys.modules[__name__] = _topics
