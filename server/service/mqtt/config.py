# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import config as _config

sys.modules[__name__] = _config
