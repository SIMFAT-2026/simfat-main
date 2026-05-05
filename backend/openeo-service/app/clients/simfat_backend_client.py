from typing import Any

import httpx

from app.core.exceptions import ExternalServiceError


class SimfatBackendClient:
    def __init__(
        self,
        base_url: str,
        ingest_path: str,
        timeout_seconds: int = 10,
        auth_token: str = "",
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.ingest_path = ingest_path.strip()
        self.timeout_seconds = timeout_seconds
        self.auth_token = auth_token.strip()

    def publish_indicator_measurement(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not self.base_url:
            raise ExternalServiceError("SIMFAT_BACKEND_URL is required to publish indicator data")
        if not self.ingest_path:
            raise ExternalServiceError(
                "SIMFAT_BACKEND_INDICATOR_INGEST_PATH is required to publish indicator data"
            )

        url = self._build_ingest_url()
        headers = {"Content-Type": "application/json"}
        if self.auth_token:
            headers["Authorization"] = f"Bearer {self.auth_token}"

        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(url, headers=headers, json=payload)
        except httpx.HTTPError as exc:
            raise ExternalServiceError(f"SIMFAT backend connectivity error: {exc}") from exc

        if response.status_code >= 400:
            message = self._extract_error_message(response)
            raise ExternalServiceError(
                f"SIMFAT backend publish failed with status {response.status_code}: {message}",
                status_code=response.status_code,
            )

        return {
            "synced": True,
            "statusCode": response.status_code,
            "targetUrl": url,
        }

    def _build_ingest_url(self) -> str:
        if self.ingest_path.startswith(("http://", "https://")):
            return self.ingest_path.rstrip("/")
        return f"{self.base_url}/{self.ingest_path.lstrip('/')}"

    def _extract_error_message(self, response: httpx.Response) -> str:
        try:
            payload = response.json()
        except ValueError:
            return response.text[:300]

        if isinstance(payload, dict):
            detail = payload.get("message") or payload.get("error") or payload.get("detail")
            if isinstance(detail, str):
                return detail
        return str(payload)[:300]
