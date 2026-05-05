from fastapi import APIRouter

from app.core.config import get_settings
from app.schemas.config import ConfigCheckResponse

router = APIRouter(tags=["config"])


@router.get("/config/check", response_model=ConfigCheckResponse)
def config_check() -> ConfigCheckResponse:
    settings = get_settings()
    missing = settings.missing_required()

    return ConfigCheckResponse(
        status="ok" if not missing else "warning",
        appEnv=settings.app_env,
        configured=not missing,
        missingFields=missing,
    )
