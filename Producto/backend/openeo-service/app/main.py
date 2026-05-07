from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import config, health, openeo
from app.core.config import get_settings
from app.core.exceptions import register_exception_handlers


def create_app() -> FastAPI:
    settings = get_settings()

    app = FastAPI(
        title="SIMFAT openEO Service",
        version="0.1.0",
        description="Microservicio para obtencion de datos satelitales desde openEO.",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allow_origins_list(),
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(health.router)
    app.include_router(config.router)
    app.include_router(openeo.router)

    register_exception_handlers(app)

    @app.get("/", tags=["system"])
    def root() -> dict[str, str]:
        return {"service": "openeo-service", "env": settings.app_env}

    return app


app = create_app()
