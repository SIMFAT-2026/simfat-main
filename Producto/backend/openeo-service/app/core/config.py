from functools import lru_cache
from typing import List

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = Field(default="local", alias="APP_ENV")
    app_port: int = Field(default=8000, alias="APP_PORT")
    app_cors_allow_origins: str = Field(default="*", alias="APP_CORS_ALLOW_ORIGINS")

    openeo_base_url: str = Field(default="", alias="OPENEO_BASE_URL")
    openeo_client_id: str = Field(default="", alias="OPENEO_CLIENT_ID")
    openeo_client_secret: str = Field(default="", alias="OPENEO_CLIENT_SECRET")
    openeo_access_token: str = Field(default="", alias="OPENEO_ACCESS_TOKEN")
    openeo_refresh_token: str = Field(default="", alias="OPENEO_REFRESH_TOKEN")
    openeo_refresh_client_id: str = Field(default="", alias="OPENEO_REFRESH_CLIENT_ID")
    openeo_refresh_client_secret: str = Field(default="", alias="OPENEO_REFRESH_CLIENT_SECRET")

    simfat_backend_url: str = Field(default="", alias="SIMFAT_BACKEND_URL")
    simfat_backend_indicator_ingest_path: str = Field(
        default="/api/indicators/measurements",
        alias="SIMFAT_BACKEND_INDICATOR_INGEST_PATH",
    )
    simfat_backend_timeout_seconds: int = Field(default=10, alias="SIMFAT_BACKEND_TIMEOUT_SECONDS")
    simfat_backend_auth_token: str = Field(default="", alias="SIMFAT_BACKEND_AUTH_TOKEN")
    simfat_backend_sync_enabled: bool = Field(default=True, alias="SIMFAT_BACKEND_SYNC_ENABLED")

    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=False,
        extra="ignore",
    )

    def missing_required(self) -> List[str]:
        required = {
            "OPENEO_BASE_URL": self.openeo_base_url,
            "OPENEO_CLIENT_ID": self.openeo_client_id,
            "OPENEO_CLIENT_SECRET": self.openeo_client_secret,
            "SIMFAT_BACKEND_URL": self.simfat_backend_url,
        }
        return [key for key, value in required.items() if not value]

    def cors_allow_origins_list(self) -> list[str]:
        origins = [item.strip() for item in self.app_cors_allow_origins.split(",") if item.strip()]
        return origins or ["*"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
