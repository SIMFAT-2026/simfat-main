# Evidencia de pruebas - Chat comunitario SDD

Fecha: 2026-05-28  
Cambio SDD: `chat-comunitario`  
Branch local: `mig-david`

## Resumen ejecutivo

La especificacion de chat comunitario fue implementada y verificada en backend, frontend y prueba manual por UI. El flujo local quedo estable para continuar con documentacion final y preparacion de push/PR.

## Evidencia automatizada

| Capa | Comando | Resultado |
|---|---|---|
| Backend Spring Boot | `mvn test` | PASS - 42 tests, 0 failures, 0 errors, 0 skipped |
| Frontend React/Vite | `npm run lint` | PASS - ESLint sin warnings |
| Frontend React/Vite | `npm run build` | PASS - build Vite correcto |

## Evidencia backend relevante

| Test | Cobertura |
|---|---|
| `AccessAdminServiceImplTest` | Region primaria, grants regionales adicionales y usuario inexistente. |
| `CommunityChatServiceImplTest` | Visibilidad de salas, seeding inicial, rechazo de usuario no verificado, autoria por identidad autenticada, presencia, moderacion por rol operacional y retencion. |
| `DashboardControllerIntegrationTest` | Sync dashboard autorizado con `PERM_DASHBOARD_SYNC_RUN` tras ajuste de seguridad del test. |

## Evidencia de entorno local

| Recurso | Estado |
|---|---|
| MongoDB local | Contenedor Docker `simfat-mongo-test` con `mongo:7`, puerto `localhost:27017`. |
| Backend local | Spring Boot en `http://localhost:8080`. |
| Frontend local | Vite en `http://127.0.0.1:5173`. |
| CORS local | Verificado `OPTIONS /api/auth/login` con `Access-Control-Allow-Origin: http://127.0.0.1:5173`. |

## Prueba manual por UI

| Caso | Resultado | Observacion |
|---|---|---|
| Login local con usuario seed | PASS | Usuario pudo autenticar contra backend local. |
| Acceso al modulo comunitario | PASS | Frontend desplego modulo comunitario. |
| Visualizacion del panel de chat | PASS | Usuario confirmo que la UI quedo correctamente funcional. |
| CORS login | PASS | Se corrigio reiniciando backend con `FRONTEND_URL` incluyendo `127.0.0.1:5173`. |

## Usuarios seed generados para prueba local

Los usuarios seed son efimeros porque el backend local se ejecuto con H2 en memoria. No se registran contrasenas en documentacion persistente para evitar dejar credenciales reutilizables.

Para regenerar usuarios:

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/auth/dev/seed-users' `
  -ContentType 'application/json' `
  -Body '{"count":2}'
```

## Notas de prueba local

Para evitar CORS en Vite con `127.0.0.1`, iniciar backend local con:

```powershell
$env:FRONTEND_URL='http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://localhost:4173'
```

Como H2 esta en scope de test, el arranque local con H2 requiere:

```powershell
mvn '-Dspring-boot.run.useTestClasspath=true' spring-boot:run
```

## Estado

Verificacion funcional aprobada para el alcance MVP. Queda pendiente QA visual formal con evidencia de captura si se requiere para entrega academica.
