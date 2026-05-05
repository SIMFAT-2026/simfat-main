from datetime import datetime, timedelta, timezone

import pytest

from app.clients.openeo_client import OpenEOClient
from app.core.exceptions import ExternalServiceError


def _build_jwt_like_token(exp: datetime) -> str:
    payload = {"exp": int(exp.timestamp())}
    # minimal token-like format; header/signature content not used by parser.
    import base64
    import json

    header = base64.urlsafe_b64encode(b'{"alg":"none"}').decode("utf-8").rstrip("=")
    body = base64.urlsafe_b64encode(json.dumps(payload).encode("utf-8")).decode("utf-8").rstrip("=")
    return f"{header}.{body}.sig"


def test_processing_token_uses_valid_static_access_token(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    token = _build_jwt_like_token(datetime.now(timezone.utc) + timedelta(minutes=15))
    client = OpenEOClient(
        base_url="https://openeo.dataspace.copernicus.eu",
        client_id="id",
        client_secret="secret",
        access_token=token,
        refresh_token="refresh",
    )

    monkeypatch.setattr(client, "_refresh_processing_access_token", lambda: "should-not-be-used")
    assert client._get_processing_access_token() == token


def test_processing_token_refreshes_when_static_token_expired(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    expired = _build_jwt_like_token(datetime.now(timezone.utc) - timedelta(minutes=10))
    client = OpenEOClient(
        base_url="https://openeo.dataspace.copernicus.eu",
        client_id="id",
        client_secret="secret",
        access_token=expired,
        refresh_token="refresh",
    )

    monkeypatch.setattr(client, "_refresh_processing_access_token", lambda: "refreshed-token")
    assert client._get_processing_access_token() == "refreshed-token"


def test_processing_token_raises_when_no_valid_config() -> None:
    client = OpenEOClient(
        base_url="https://openeo.dataspace.copernicus.eu",
        client_id="id",
        client_secret="secret",
    )

    with pytest.raises(ExternalServiceError) as ex:
        client._get_processing_access_token()

    assert "OPENEO_ACCESS_TOKEN" in str(ex.value) or "OPENEO_REFRESH_TOKEN" in str(ex.value)
