# Configuracion Supabase Storage - Reportes Ciudadanos

- Fecha: 2026-04-22
- Version: 1.0
- Repositorio: `simfat-backend`

## Objetivo

Dejar trazable la configuracion minima para que la carga de imagenes de reportes ciudadanos persista en Supabase Storage y no degrade a fallback local.

## Variables requeridas en `.env`

```env
APP_STORAGE_SUPABASE_ENABLED=true
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service_role_key_real>
SUPABASE_STORAGE_BUCKET=citizen-reports
```

## Validaciones importantes

1. `SUPABASE_URL` debe ser la URL base del proyecto, sin `/rest/v1`.
2. `SUPABASE_SERVICE_ROLE_KEY` debe ser la clave completa real (sin espacios extra).
3. `SUPABASE_STORAGE_BUCKET` debe existir en el proyecto.
4. Si se usaran URLs publicas directas, el bucket debe estar en modo publico o con politica de lectura equivalente.

## Diagnostico del estado actual

- Backend ya soporta upload via `SupabaseStorageService`.
- Si Storage falla, el backend mantiene creacion de reporte para no perder continuidad operativa.
- Frontend reintenta crear el reporte sin archivos si falla el envio con adjuntos.
- Pendiente operacional: terminar de alinear variables de entorno Supabase en entorno local/evaluacion.

## Prueba rapida sugerida

1. Levantar backend.
2. Crear reporte con 1 imagen desde frontend.
3. Recargar pagina y validar que el reporte sigue en "Seguimiento de reportes".
4. Verificar en `GET /api/citizen-reports` que `photos` contenga URL(s) o referencia esperada.
