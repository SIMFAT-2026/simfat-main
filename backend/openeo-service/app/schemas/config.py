from typing import List, Literal

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: Literal["ok"]
    service: str


class ConfigCheckResponse(BaseModel):
    status: Literal["ok", "warning"]
    appEnv: str
    configured: bool
    missingFields: List[str]
