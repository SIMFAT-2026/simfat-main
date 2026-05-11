# QA y Evidencias - Iteraci?n 2026-04-22

Fecha: 2026-04-22  
Version: 1.0  
Objetivo: registrar pruebas de QA ejecutadas y evidencia para entrega DUOC.

## 1) Pruebas ejecutadas en frontend (simfat-web)

### 1.1 Calidad de c?digo

- Comando: `npm run lint`
- Resultado: OK

### 1.2 Build de produccion

- Comando: `npm run build`
- Resultado: OK
- Observacion: bundle optimizado con `manualChunks` para separar `map-vendor`, `charts-vendor`, `react-core`.

### 1.3 Verificacion funcional principal

- Ruta `/territorio` con mapa Leaflet + dashboard embebido.
- Ruta `/comunidad` con mural/recursos/contactos funcionales.
- Ruta `/reportes` con formulario geolocalizado + carga de fotos + estados.

## 2) Pruebas ejecutadas en backend (simfat-backend)

### 2.1 Compilacion

- Comando: `mvn -q -DskipTests compile`
- Resultado: OK

### 2.2 Cobertura de pruebas disponible en repo

Se identifican suites de prueba en:

- `src/test/java/.../service/impl/*`
- `src/test/java/.../repository/*`
- `src/test/java/.../controller/*`
- `src/test/java/.../integration/openeo/*`

## 3) Checklist de evidencia DUOC

- [x] Diagrama de arquitectura actualizado
- [x] MER actualizado
- [x] Diccionario de datos actualizado
- [x] Modelo l?gico actualizado
- [x] Documento de scripts para creacion e insercion de prueba
- [x] Documento QA con comandos y resultados

## 4) Evidencia sugerida a anexar en GitHub/Drive

1. Captura de consola de `npm run lint` y `npm run build`.
2. Captura de mapa territorial en `/territorio`.
3. Captura de flujo comunidad (`/comunidad`).
4. Captura de flujo reportes (`/reportes`).
5. Captura de `mvn -q -DskipTests compile` backend.
