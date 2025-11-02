# vremennaya prokladka dlya obratnoi sovmestimosti
import sys

from app.api.routers.mqtt import client as _client

sys.modules[__name__] = _client
