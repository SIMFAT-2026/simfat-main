# Avance Auth Frontend - SIMFAT

Fecha: 2026-04-15
Repositorio: `simfat-web`
Rama: `develop`

## Resumen

Se completo la migracion del frontend desde auth local de prueba hacia integraci?n real con backend JWT, incluyendo mejoras de UX para un flujo mas intuitivo de inicio de sesi?n y registro.

## Implementado

1. Integraci?n JWT con backend
- Login, register, forgot, reset, me y logout conectados a `simfat-backend`.
- Inyeccion automatica de `Authorization: Bearer <accessToken>` en requests autenticados.
- Soporte de refresh automatico con `POST /api/auth/refresh` al detectar `401` por access token expirado.
- Cola de reintentos para requests concurrentes durante refresh.
- Cierre de sesi?n local cuando refresh falla.

2. Proteccion de rutas y sesi?n
- Rutas privadas y publicas con guards.
- Bootstrap de sesi?n al cargar la app (`/api/auth/me`).
- Persistencia de sesi?n centralizada en `tokenStorage`.

3. UX de autenticaci?n
- Mensajeria de instrucciones para contraseñas en registro.
- Checklist de requisitos de password.
- Barra indicadora de seguridad de contraseña (debil/media/fuerte).
- Mostrar/ocultar contraseña en login y registro.
- Opcion "Recordar usuario" en login (persistencia de correo en localStorage).

4. Desarrollo y pruebas
- Turnstile Cloudflare configurable por `.env`.
- Herramienta dev opcional para crear usuarios de prueba desde backend (`/api/auth/dev/seed-users`), sin hardcode.
- Lint y build verificados en verde.

## Archivos clave tocados

- `src/api/axiosClient.js`
- `src/api/endpoints.js`
- `src/services/authService.js`
- `src/auth/AuthContext.jsx`
- `src/auth/ProtectedRoute.jsx`
- `src/auth/PublicOnlyRoute.jsx`
- `src/auth/tokenStorage.js`
- `src/pages/auth/LoginPage.jsx`
- `src/pages/auth/RegisterPage.jsx`
- `src/pages/auth/ForgotPasswordPage.jsx`
- `src/pages/auth/ResetPasswordPage.jsx`
- `src/utils/passwordPolicy.js`
- `src/styles/global.css`
- `README.md`

## Variables de entorno relevantes

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081
VITE_API_URL=http://localhost:8081
VITE_AUTH_TURNSTILE_ENABLED=true
VITE_TURNSTILE_SITE_KEY=1x00000000000000000000AA
VITE_AUTH_DEV_TOOLS_ENABLED=true
```

## Resultado

Frontend de SIMFAT quedo listo para operar con autenticaci?n backend segura (JWT + refresh) y con una experiencia de usuario de auth significativamente mas clara y guiada para pruebas y uso diario.
