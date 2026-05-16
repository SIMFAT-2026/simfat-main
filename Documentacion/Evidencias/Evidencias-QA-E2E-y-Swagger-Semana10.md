# Evidencias QA E2E y Swagger - Semana 10

Fecha: 2026-05-15

## 1) Evidencia pruebas Swagger/OpenAPI

Comando ejecutado:

```powershell
mvn -q "-Dtest=OpenApiSwaggerIntegrationTest,SecurityAuthorizationIntegrationTest" test
```

Resultados (surefire):

- `com.simfat.backend.controller.OpenApiSwaggerIntegrationTest`
  - Tests run: 2
  - Failures: 0
  - Errors: 0
- `com.simfat.backend.controller.SecurityAuthorizationIntegrationTest`
  - Tests run: 4
  - Failures: 0
  - Errors: 0

Archivos fuente de evidencia:

- `Producto/backend/simfat-backend/target/surefire-reports/com.simfat.backend.controller.OpenApiSwaggerIntegrationTest.txt`
- `Producto/backend/simfat-backend/target/surefire-reports/com.simfat.backend.controller.SecurityAuthorizationIntegrationTest.txt`

## 2) Evidencia E2E tecnico (frontend + backend)

Comando de build frontend:

```powershell
npm run build
```

Resultado:

- Build exitoso (`vite build`) sin errores.
- Bundle generado con pagina `AccessControlPage` incluida.

## 3) Evidencia funcional de control de accesos

- Flujo validado en UI:
  - tabla usuarios
  - perfil predefinido
  - switch verificado
  - guardado de roles
- Rediseno aplicado para reducir complejidad (panel compacto + ajustes avanzados colapsables).

## 4) Conclusiones QA

- Estado general: `PASS` para seguridad RBAC base y disponibilidad Swagger.
- Riesgo residual conocido:
  - pendiente incremento futuro de verificacion avanzada de usuario (documento/identidad/trust score).
