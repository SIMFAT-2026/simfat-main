# Mapeo de Autenticación SIMFAT (Frontend -> Backend JWT)

Fecha: 2026-04-15

## Objetivo

Migrar de autenticación local de desarrollo a autenticación real con backend (`Simfat-backend`) usando JWT, buenas prácticas de seguridad y soporte de recuperación de contraseña.

## Estado del Frontend (simfat-web)

El frontend ya quedó optimizado para backend:

- Interceptor `Authorization: Bearer <accessToken>` en Axios.
- Sesión centralizada en `src/auth/tokenStorage.js`.
- Flujo de auth desacoplado en `src/services/authService.js`.
- Rutas protegidas y públicas con guards.
- Turnstile (Cloudflare) habilitable por variable de entorno.
- Herramienta dev opcional para crear usuarios de prueba via API (sin hardcode).

## Contrato esperado de API

Base URL:

- `http://localhost:8081` (o variable equivalente)

Endpoints:

1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `POST /api/auth/forgot-password`
4. `POST /api/auth/reset-password`
5. `GET /api/auth/me`
6. `POST /api/auth/logout`
7. `POST /api/auth/dev/seed-users` (solo desarrollo)

### Request sugeridos

`POST /api/auth/register`

```json
{
  "name": "Nombre Apellido",
  "email": "user@simfat.cl",
  "password": "PasswordFuerte!123",
  "captchaToken": "..."
}
```

`POST /api/auth/login`

```json
{
  "email": "user@simfat.cl",
  "password": "PasswordFuerte!123",
  "captchaToken": "..."
}
```

`POST /api/auth/forgot-password`

```json
{
  "email": "user@simfat.cl",
  "captchaToken": "..."
}
```

`POST /api/auth/reset-password`

```json
{
  "email": "user@simfat.cl",
  "token": "reset-token",
  "newPassword": "NuevaClave!456",
  "captchaToken": "..."
}
```

`POST /api/auth/dev/seed-users`

```json
{
  "count": 3
}
```

### Response de login/register (envoltura ApiResponse)

```json
{
  "success": true,
  "message": "Autenticación exitosa",
  "data": {
    "user": {
      "id": "uuid",
      "name": "Nombre Apellido",
      "email": "user@simfat.cl",
      "roles": ["USER"]
    },
    "accessToken": "jwt-access",
    "refreshToken": "jwt-refresh-opcional",
    "expiresAt": "2026-04-15T20:15:00Z"
  },
  "timestamp": "2026-04-15T19:15:00Z"
}
```

## Seguridad mínima recomendada

1. Password hashing con `BCrypt` (cost >= 10).
2. JWT access token corto (10-15 min).
3. Refresh token rotatorio y revocable.
4. Refresh token en cookie `HttpOnly + Secure + SameSite`.
5. Rate limit por IP/cuenta para login y forgot-password.
6. Mensaje genérico en forgot-password para evitar enumeración de correos.
7. Reset token aleatorio con expiración corta (15 min), persistido con hash.
8. Auditoría de eventos críticos de auth.
9. Endpoint dev de seed habilitado solo en `dev`/`local`.

## Prompt sugerido para Simfat-backend

Usa este prompt con tu agente backend:

```text
Necesito implementar autenticación completa y segura en Simfat-backend (Spring Boot + MongoDB) para integrarla con simfat-web.

Contexto:
- Frontend ya consume estos endpoints:
  - POST /api/auth/register
  - POST /api/auth/login
  - POST /api/auth/forgot-password
  - POST /api/auth/reset-password
  - GET /api/auth/me
  - POST /api/auth/logout
  - POST /api/auth/dev/seed-users (solo desarrollo)
- El frontend espera respuestas envueltas en ApiResponse<T>.
- Login y register deben responder data.user + data.accessToken + data.refreshToken(opcional) + data.expiresAt.
- forgot-password no debe revelar si el correo existe.

Objetivo técnico:
1) Diseñar módulo auth con arquitectura limpia (controller/service/repository/security/dto).
2) Implementar registro, login, perfil actual, logout, forgot/reset password.
3) Implementar JWT seguro:
   - Access token corto (10-15 min)
   - Refresh token rotatorio
   - Revocación de refresh tokens
4) Hash de contraseñas con BCrypt.
5) Validaciones robustas DTO + manejo de errores consistente.
6) Rate limiting para login y recuperación.
7) Endpoint dev para seed users NO hardcodeados:
   - POST /api/auth/dev/seed-users con body {count}
   - Generar usuarios aleatorios dinámicamente (email + password fuerte), guardarlos hasheados y devolver lista en modo desarrollo
   - Bloquear endpoint fuera de perfil dev/local
8) Integrar verificación de Turnstile opcional (feature flag por env).
9) Incluir migraciones/índices Mongo necesarios y pruebas unitarias/integración para casos críticos.
10) Documentar variables de entorno y ejemplos curl.

Entregables:
- Código completo de auth en backend
- Tests pasando
- README actualizado con flujo de autenticación
- Tabla de amenazas y mitigaciones aplicadas
- Checklist de hardening pendiente para producción

Importante:
- No usar credenciales hardcodeadas.
- No devolver información sensible en errores.
- Mantener compatibilidad con ApiResponse<T> usado por el frontend.
```
