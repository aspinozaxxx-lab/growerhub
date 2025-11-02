# vremennaya prokladka dlya obratnoi sovmestimosti
import importlib
import sys

_NEW_PREFIX = "app.api.routers.mqtt"
_ALIASES = [
    "",
    "config",
    "interfaces",
    "topics",
    "serialization",
    "store",
    "client",
    "router",
    "lifecycle",
    "handlers",
    "handlers.ack",
    "handlers.device_state",
]

for _alias in _ALIASES:
    _target = f"{_NEW_PREFIX}{'.' + _alias if _alias else ''}"
    _module = importlib.import_module(_target)
    sys.modules[f"{__name__}{'.' + _alias if _alias else ''}"] = _module

globals().update(sys.modules[__name__].__dict__)
