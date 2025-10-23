"""
A lightweight stub of the ``httpx`` library tailored for the GrowerHub
test suite.  When the real ``httpx`` package is available it is imported
and re-exported transparently; otherwise this stub provides the minimal
surface required by the existing tests.
"""

from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path

_FORCE_STUB = os.getenv("GROWERHUB_FORCE_HTTPX_STUB", "0").lower() in {"1", "true", "yes", "on"}
_USE_STUB = True

if not _FORCE_STUB:
    _module_dir = Path(__file__).resolve().parent
    _removed_path: str | None = None
    if sys.path and Path(sys.path[0]).resolve() == _module_dir:
        _removed_path = sys.path.pop(0)
    _existing_module = sys.modules.pop(__name__, None)
    try:
        _spec = importlib.util.find_spec("httpx")
    finally:
        if _existing_module is not None:
            sys.modules[__name__] = _existing_module
        if _removed_path is not None:
            sys.path.insert(0, _removed_path)
    if _spec and _spec.origin and Path(_spec.origin).resolve() != Path(__file__).resolve():
        _module = importlib.util.module_from_spec(_spec)
        if _spec.loader is None:
            raise ImportError("httpx spec loader is missing")
        sys.modules[_spec.name] = _module
        _spec.loader.exec_module(_module)  # type: ignore[attr-defined]
        sys.modules[__name__] = _module
        globals().update(_module.__dict__)
        _USE_STUB = False

if _USE_STUB:
    import asyncio
    from dataclasses import dataclass
    from typing import Any, Dict, List, Optional

    from app.main import (
        update_device_status,
        get_all_devices,
        get_device_settings,
        update_device_settings,
        check_firmware_update,
    )
    from app.models.database_models import DeviceStatus, DeviceSettings, DeviceInfo
    import app.core.database as db

    class ASGITransport:
        """A trivial transport holding a reference to the FastAPI app.

        In the real ``httpx`` library the ASGITransport is responsible for
        sending HTTP requests to an ASGI application.  Here we merely
        record the ``app`` attribute so that the client knows which
        application to target.  No other behaviour is provided.
        """

        def __init__(self, *, app: Any) -> None:
            self.app = app

    @dataclass
    class Response:
        """A minimal HTTP response container.

        The ``status_code`` attribute holds the numeric HTTP status and
        ``_json`` contains the parsed JSON payload returned by the
        endpoint.  The ``json()`` method returns the stored JSON data.
        """

        status_code: int
        _json: Any

        def json(self) -> Any:
            return self._json

    class AsyncClient:
        """A lightweight asynchronous HTTP client for in‑process calls.

        The client dispatches requests based solely on the URL path.  Only
        the endpoints used by the test suite are supported.  Each
        operation creates a new database session via the patched
        ``db.SessionLocal`` to ensure isolation between requests.  The
        client itself is an asynchronous context manager so that it can
        easily be used with ``async with`` blocks in the tests.
        """

        def __init__(self, *, transport: ASGITransport, base_url: str = "http://testserver") -> None:
            # Record the application from the transport.  While unused in
            # dispatching (we call the endpoints directly), it maintains API
            # compatibility with the real httpx.AsyncClient initializer.
            self._app = transport.app
            self.base_url = base_url.rstrip('/')

        async def __aenter__(self) -> "AsyncClient":
            return self

        async def __aexit__(self, exc_type, exc: Optional[BaseException], tb) -> None:
            # Nothing to clean up for this stub.  Real httpx clients would
            # close network connections here.
            return None

        async def get(self, url: str, *, params: Optional[Dict[str, Any]] = None) -> Response:
            return await self._request("GET", url, params=params)

        async def post(self, url: str, *, json: Optional[Dict[str, Any]] = None) -> Response:
            return await self._request("POST", url, json=json)

        async def put(self, url: str, *, json: Optional[Dict[str, Any]] = None) -> Response:
            return await self._request("PUT", url, json=json)

        async def _request(
            self,
            method: str,
            url: str,
            *,
            params: Optional[Dict[str, Any]] = None,
            json: Optional[Dict[str, Any]] = None,
        ) -> Response:
            """Dispatch a request to the appropriate FastAPI endpoint.

            Only the subset of endpoints exercised by the test suite are
            implemented.  Unsupported URLs or methods result in a 404
            response.
            """
            # Strip the base URL if the caller passed a fully qualified URL
            if url.startswith("http://") or url.startswith("https://"):
                # Find the third slash to isolate the path portion
                path_start = url.find('/', url.find('//') + 2)
                if path_start != -1:
                    path = url[path_start:]
                else:
                    path = '/'  # treat bare domain as root
            else:
                path = url

            # Normalise path by stripping any query string or fragment
            path = path.split("?")[0].split("#")[0]
            path = path.rstrip('/') if path != '/' else path

            # Handle GET /api/devices
            if method == "GET" and path == "/api/devices":
                session = db.SessionLocal()
                try:
                    result = await get_all_devices(db=session)
                    json_result: List[Dict[str, Any]] = []
                    for item in result:
                        if hasattr(item, "dict"):
                            json_result.append(item.dict())
                        elif hasattr(item, "model_dump"):
                            json_result.append(item.model_dump())  # type: ignore[no-untyped-call]
                        else:
                            # Fallback: convert dataclass to dict
                            json_result.append(item.__dict__)
                    return Response(200, json_result)
                finally:
                    session.close()

            # Handle device-specific routes: /api/device/{device_id}/...
            parts = [segment for segment in path.split("/") if segment]
            # Expect parts like ['api', 'device', '{device_id}', 'status'|...]
            if len(parts) >= 4 and parts[0] == "api" and parts[1] == "device":
                device_id = parts[2]
                action = parts[3]
                session = db.SessionLocal()
                try:
                    if action == "status" and method == "POST":
                        if json is None:
                            return Response(400, {"detail": "Missing JSON body"})
                        # Construct a DeviceStatus model from the JSON payload
                        status_model = DeviceStatus(**json)
                        data = await update_device_status(device_id, status_model, db=session)
                        return Response(200, data)

                    if action == "settings":
                        if method == "GET":
                            data = await get_device_settings(device_id, db=session)
                            return Response(200, data)
                        if method == "PUT":
                            if json is None:
                                return Response(400, {"detail": "Missing JSON body"})
                            settings_model = DeviceSettings(**json)
                            data = await update_device_settings(device_id, settings_model, db=session)
                            return Response(200, data)

                    if action == "firmware" and method == "GET":
                        data = await check_firmware_update(device_id, db=session)
                        return Response(200, data)
                finally:
                    session.close()

            # No matching route
            return Response(404, {"detail": "Not Found"})

    # The real httpx library exposes a number of additional symbols such as
    # ByteStream, Timeout and various type aliases.  To satisfy imports in
    # dependent code we provide minimal placeholders here.  If your tests
    # require more of the API surface simply extend these definitions.

    class ByteStream:
        """Placeholder for httpx.ByteStream used in starlette.testclient."""

        def __init__(self, data: bytes) -> None:
            self.data = data

        def read(self) -> bytes:
            return self.data

    class BaseTransport:
        """Placeholder base class for httpx transports."""

        def handle_request(self, request: Any) -> Any:
            raise NotImplementedError

    USE_CLIENT_DEFAULT: int = 0  # Sentinel used by httpx for default argument values

    __all__ = [
        "ASGITransport",
        "AsyncClient",
        "Response",
        "ByteStream",
        "BaseTransport",
        "USE_CLIENT_DEFAULT",
    ]
