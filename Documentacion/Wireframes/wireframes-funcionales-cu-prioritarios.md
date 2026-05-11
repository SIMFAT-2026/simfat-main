# Wireframes Funcionales (Baja Fidelidad) - SIMFAT

Fecha: 2026-05-11  
Objetivo: dejar wireframes/documento visual funcional alineado a CU prioritarios del sistema.

## 1. Pantallas cubiertas

- CU01: Login
- CU04: Registro
- CU05: Recuperar contraseña / reset
- CU02 + CU06: Dashboard y métricas históricas
- CU03 + CU07: Territorio (mapa, capas, zonas de riesgo)
- CU08 + CU10: Alertas (reglas e historial)
- CU11: Ingesta externa (vista operativa admin)
- CU15 (base): Gestión de usuarios/roles (wireframe conceptual)

## 2. Wireframe CU01 - Login

```text
+------------------------------------------------------+
| SIMFAT                                               |
| prevención y alerta temprana                         |
|------------------------------------------------------|
| [ Correo electronico                         ]       |
| [ contraseña                                 ]       |
| [ ] Recordarme                                         |
| [ Iniciar sesión ]                                    |
|------------------------------------------------------|
| Olvide mi contraseña   |   Registrarme               |
+------------------------------------------------------+
```

## 3. Wireframe CU04 - Registro

```text
+------------------------------------------------------+
| Crear cuenta SIMFAT                                  |
|------------------------------------------------------|
| [ Nombre completo                            ]       |
| [ Correo electronico                         ]       |
| [ contraseña                                 ]       |
| [ Confirmar contraseña                       ]       |
| [ Captcha / Turnstile ]                               |
| [ Registrarme ]                                       |
|------------------------------------------------------|
| Ya tienes cuenta? [Iniciar sesión]                   |
+------------------------------------------------------+
```

## 4. Wireframe CU05 - Recuperar contraseña

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
| Restablecer contraseña                               |
|------------------------------------------------------|
| [ Token ]                                            |
| [ Nueva contraseña ]                                 |
| [ Confirmar nueva contraseña ]                       |
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
|   - Capa de criticidad por región                                                 |
|   - Tooltip: región, score, ultima actualización                                  |
|----------------------------------------------------------------------------------|
| Panel lateral:                                                                    |
|  - Prioridad operativa                                                            |
|  - Eventos cercanos                                                               |
|  - Estado datos (vivo/cache)                                                      |
+----------------------------------------------------------------------------------+
```

## 7. Wireframe CU08/CU10 - módulo alertas (reglas + historial)

```text
+----------------------------------------------------------------------------------+
| Alertas                                                                           |
|----------------------------------------------------------------------------------|
| Filtros: [Region] [Nivel] [Desde] [Hasta] [Actualizar] [Limpiar]                 |
|----------------------------------------------------------------------------------|
| Tabla historial: Fecha | Region | Nivel | Fuente | Descripcion | acciónes        |
|----------------------------------------------------------------------------------|
| Formulario regla/alerta:                                                          |
| [Region] [Umbral perdida %] [Umbral eventos calor] [Activa SI/NO] [Guardar]      |
+----------------------------------------------------------------------------------+
```

## 8. Wireframe CU11 - Ingesta API externa (admin técnico)

```text
+----------------------------------------------------------------------------------+
| Ingesta Externa (OpenEO/NASA/GFW)                                                |
|----------------------------------------------------------------------------------|
| parámetros: [Fuente] [Region/AOI] [Fecha inicio] [Fecha fin] [Indicador]         |
| [ Ejecutar ingesta ] [ válidar normalizacion ]                                   |
|----------------------------------------------------------------------------------|
| Estado ultima corrida:                                                            |
| - jobId | estado | registros recibidos | registros válidos | errores             |
|----------------------------------------------------------------------------------|
| Log resumido y acciónes de reintento                                              |
+----------------------------------------------------------------------------------+
```

## 9. Wireframe CU15 - Gestión de usuarios (conceptual)

```text
+----------------------------------------------------------------------------------+
| administración de usuarios                                                       |
|----------------------------------------------------------------------------------|
| Filtros: [Nombre/Correo] [Rol] [Estado]                                          |
|----------------------------------------------------------------------------------|
| Tabla: Nombre | Correo | Rol | Estado | Ultimo acceso | acciónes                |
|----------------------------------------------------------------------------------|
| acciónes: [Crear usuario] [Editar] [Deshabilitar] [Reset password]               |
|----------------------------------------------------------------------------------|
| Modal roles/permisos:                                                             |
| [Usuario General] [Administrador] + matriz de permisos                           |
+----------------------------------------------------------------------------------+
```

## 10. Nota de uso académico

Estos wireframes son de baja fidelidad y sirven como respaldo formal de diseño funcional.
Se recomienda exportarlos a formato PDF/DOCX para la entrega final s? la pauta lo exige.


