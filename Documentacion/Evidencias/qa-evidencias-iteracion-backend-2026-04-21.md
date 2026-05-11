# Evidencias QA - Iteraci?n SIMFAT

- Fecha: 2026-04-21
- Version: 1.0
- Objetivo: dejar trazabilidad de verificaciones t?cnicas para cierre de avance.

## 1) Frontend (`simfat-web`)

Comandos ejecutados:

```bash
npm run lint
npm run build
```

Resultado:

- `lint`: OK, sin warnings permitidos (`--max-warnings 0`).
- `build`: OK, bundle de produccion generado.
- Observacion relevante de performance:
  - chunk `map-vendor` separado, permitiendo carga diferida del m?dulo territorial.

## 2) Backend (`simfat-backend`)

Comando ejecutado:

```bash
mvn -q -DskipTests compile
```

Resultado:

- Compilacion exitosa sin errores.
- Validacion de integridad basica de c?digo posterior a actualizaciones de documentaci?n y configuracion de datos.

## 3) Evidencias sugeridas para carpeta DUOC

- Captura de terminal con `npm run lint` en verde.
- Captura de terminal con `npm run build` en verde.
- Captura de terminal con `mvn -q -DskipTests compile` exitoso.
- Captura de MongoDB Compass o shell mostrando colecciones activas.
- Captura de PostgreSQL mostrando tablas `app_users`, `refresh_tokens`, `password_reset_tokens`.

## 4) Riesgos pendientes para siguiente ronda QA

- Pruebas E2E con backend/openeo caidos para validar degradacion controlada.
- Pruebas funcionales de contratos nuevos de capas territoriales y reportes ciudadanos.
- Pruebas de autorizaci?n por rol en flujos comunitarios y de reportes.
