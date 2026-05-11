# Wireframes Funcionales (Baja Fidelidad) - SIMFAT

Fecha: 2026-05-11  
Objetivo: dejar wireframes/documento visual funcional alineado a CU prioritarios del sistema.

## 1. Pantallas cubiertas

- CU01: Login
- CU04: Registro
- CU05: Recuperar contrasena / reset
- CU02 + CU06: Dashboard y metricas historicas
- CU03 + CU07: Territorio (mapa, capas, zonas de riesgo)
- CU08 + CU10: Alertas (reglas e historial)
- CU11: Ingesta externa (vista operativa admin)
- CU15 (base): Gestion de usuarios/roles (wireframe conceptual)

## 2. Wireframe CU01 - Login

```text
+------------------------------------------------------+
| SIMFAT                                               |
| Prevencion y alerta temprana                         |
|------------------------------------------------------|
| [ Correo electronico                         ]       |
| [ Contrasena                                 ]       |
| [ ] Recordarme                                         |
| [ Iniciar sesion ]                                    |
|------------------------------------------------------|
| Olvide mi contrasena   |   Registrarme               |
+------------------------------------------------------+
```

## 3. Wireframe CU04 - Registro

```text
+------------------------------------------------------+
| Crear cuenta SIMFAT                                  |
|------------------------------------------------------|
| [ Nombre completo                            ]       |
| [ Correo electronico                         ]       |
| [ Contrasena                                 ]       |
| [ Confirmar contrasena                       ]       |
| [ Captcha / Turnstile ]                               |
| [ Registrarme ]                                       |
|------------------------------------------------------|
| Ya tienes cuenta? [Iniciar sesion]                   |
+------------------------------------------------------+
```

## 4. Wireframe CU05 - Recuperar contrasena

```text
+------------------------------------------------------+
| Recuperar acceso                                     |
|------------------------------------------------------|
| [ Correo registrado                          ]       |
| [ Enviar enlace ]                                     |
|------------------------------------------------------|
| Estado: "Si el correo existe, te enviaremos..."      |
+------------------------------------------------------+

+------------------------------------------------------+
| Restablecer contrasena                               |
|------------------------------------------------------|
| [ Token ]                                            |
| [ Nueva contrasena ]                                 |
| [ Confirmar nueva contrasena ]                       |
| [ Guardar ]                                          |
+------------------------------------------------------+
```

## 5. Wireframe CU02/CU06 - Dashboard estadistico

```text
+----------------------------------------------------------------------------------+
| Navbar | Filtros: [Region] [Desde] [Hasta] [Indicador] [Granularidad] [Aplicar] |
|----------------------------------------------------------------------------------|
| KPI1 Total alertas | KPI2 Hectareas perdidas | KPI3 Criticidad | KPI4 Frescura  |
|----------------------------------------------------------------------------------|
| Grafico tendencia perdida forestal            | Donut resumen alertas            |
|----------------------------------------------------------------------------------|
| Serie temporal indicador (NDVI/NDMI)          | Regiones criticas (tabla)        |
+----------------------------------------------------------------------------------+
```

## 6. Wireframe CU03/CU07 - Territorio y zonas de riesgo

```text
+----------------------------------------------------------------------------------+
| Territorio                                                                       |
|----------------------------------------------------------------------------------|
| Filtros: [Region] [Capas: NDVI][NDMI][LOSS][ALERTAS][REPORTES] [Actualizar]     |
|----------------------------------------------------------------------------------|
|                                  MAPA                                            |
|   - Pins de alertas                                                               |
|   - Capa de criticidad por region                                                 |
|   - Tooltip: region, score, ultima actualizacion                                  |
|----------------------------------------------------------------------------------|
| Panel lateral:                                                                    |
|  - Prioridad operativa                                                            |
|  - Eventos cercanos                                                               |
|  - Estado datos (vivo/cache)                                                      |
+----------------------------------------------------------------------------------+
```

## 7. Wireframe CU08/CU10 - Modulo alertas (reglas + historial)

```text
+----------------------------------------------------------------------------------+
| Alertas                                                                           |
|----------------------------------------------------------------------------------|
| Filtros: [Region] [Nivel] [Desde] [Hasta] [Actualizar] [Limpiar]                 |
|----------------------------------------------------------------------------------|
| Tabla historial: Fecha | Region | Nivel | Fuente | Descripcion | Acciones        |
|----------------------------------------------------------------------------------|
| Formulario regla/alerta:                                                          |
| [Region] [Umbral perdida %] [Umbral eventos calor] [Activa SI/NO] [Guardar]      |
+----------------------------------------------------------------------------------+
```

## 8. Wireframe CU11 - Ingesta API externa (admin tecnico)

```text
+----------------------------------------------------------------------------------+
| Ingesta Externa (OpenEO/NASA/GFW)                                                |
|----------------------------------------------------------------------------------|
| Parametros: [Fuente] [Region/AOI] [Fecha inicio] [Fecha fin] [Indicador]         |
| [ Ejecutar ingesta ] [ Validar normalizacion ]                                   |
|----------------------------------------------------------------------------------|
| Estado ultima corrida:                                                            |
| - jobId | estado | registros recibidos | registros validos | errores             |
|----------------------------------------------------------------------------------|
| Log resumido y acciones de reintento                                              |
+----------------------------------------------------------------------------------+
```

## 9. Wireframe CU15 - Gestion de usuarios (conceptual)

```text
+----------------------------------------------------------------------------------+
| Administracion de usuarios                                                       |
|----------------------------------------------------------------------------------|
| Filtros: [Nombre/Correo] [Rol] [Estado]                                          |
|----------------------------------------------------------------------------------|
| Tabla: Nombre | Correo | Rol | Estado | Ultimo acceso | Acciones                |
|----------------------------------------------------------------------------------|
| Acciones: [Crear usuario] [Editar] [Deshabilitar] [Reset password]               |
|----------------------------------------------------------------------------------|
| Modal roles/permisos:                                                             |
| [Usuario General] [Administrador] + matriz de permisos                           |
+----------------------------------------------------------------------------------+
```

## 10. Nota de uso academico

Estos wireframes son de baja fidelidad y sirven como respaldo formal de diseno funcional.
Se recomienda exportarlos a formato PDF/DOCX para la entrega final si la pauta lo exige.
