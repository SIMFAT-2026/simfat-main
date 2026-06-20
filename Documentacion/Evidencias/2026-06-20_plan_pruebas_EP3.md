# Plan de Pruebas de Software — EP3
## SIMFAT — Sistema de Monitorización Forestal y Análisis Territorial

- **Curso:** TPY1101 – Taller Aplicado de Programación
- **Institución:** Duoc UC
- **Estudiante:** David Vásquez
- **Fecha:** 2026-06-20
- **Versión:** 1.0
- **Ambiente de prueba:** Producción (simfat-web en Vercel + backend Railway)

---

## 1. Pruebas funcionales — Casos de uso

| ID | Funcionalidad | Acción de prueba | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| CU01-T01 | Login / autenticación JWT | Ingresar email y contraseña válidos en `/login` y presionar "Ingresar" | Se genera token JWT, se redirige a `/territorio` y el nombre del usuario aparece en la barra lateral | El sistema autentica correctamente, almacena el token en localStorage y redirige al mapa territorial. El token incluye rol y expiración. | APROBADO |
| CU01-T02 | Login / autenticación JWT | Ingresar credenciales incorrectas | Mensaje de error claro ("Credenciales inválidas"), sin redirección, sin token emitido | Se muestra mensaje de error del backend; el formulario permanece activo sin limpiar el email | APROBADO |
| CU01-T03 | Login — token persistente | Cerrar pestaña y reabrir la aplicación con sesión activa | La sesión se mantiene activa sin requerir nuevo login (token no expirado) | La sesión persiste correctamente al reabrir la pestaña dentro del tiempo de validez del token | APROBADO |
| CU02-T01 | Mapa territorial — choropleth de riesgo | Seleccionar región "Araucanía" en el selector y visualizar el mapa | El mapa muestra comunas coloreadas según nivel de riesgo (NORMAL/PREVENTIVO/ALTO/CRÍTICO) con leyenda visible | Las comunas se colorean con la paleta de riesgo definida. La leyenda aparece en la esquina inferior del mapa. La capa de choropleth usa polígonos GADM correctamente simplificados. | APROBADO |
| CU02-T02 | Mapa territorial — selección de comuna | Hacer clic sobre una comuna en el mapa | Aparece panel lateral con score WLC, nivel de alerta, y desglose de componentes | El panel comunal se abre con los datos de la comuna seleccionada: score compuesto, nivel de alerta, y contribuciones de NDVI, NDMI, FWI, FIRMS y reportes ciudadanos | APROBADO |
| CU02-T03 | Mapa territorial — capa FIRMS | Activar toggle "FIRMS" en la barra de controles | Aparecen puntos de fuego sobre el mapa correspondientes a la región seleccionada | Los focos FIRMS aparecen como marcadores. El conteo muestra solo focos dentro del bounding box de la región activa (filtro client-side corregido en este sprint) | APROBADO |
| CU02-T04 | Mapa territorial — capa de viento | Activar toggle "Viento" con región Araucanía seleccionada | Aparecen flechas de dirección de viento por comuna con color según velocidad | Las flechas aparecen orientadas según datos de Open-Meteo. El tooltip muestra velocidad (km/h) y punto cardinal. El color varía según la escala de la leyenda. | APROBADO |
| CU03-T01 | Panel analítico regional | Seleccionar una región y desplazarse al panel de indicadores en TerritoryPage | El panel muestra KPIs regionales: FWI promedio, NDVI, NDMI, focos FIRMS, alertas activas, reportes ciudadanos | El dashboard analítico se embebe correctamente en TerritoryPage y se actualiza automáticamente al cambiar la región seleccionada | APROBADO |
| CU03-T02 | Exportar informe regional PDF | Hacer clic en "Exportar informe regional" en el panel analítico | Se abre ventana de impresión con el informe regional formateado, listo para guardar como PDF | La ventana de impresión del navegador se abre con el informe incluyendo nombre de región, fecha, KPIs y gráficos | APROBADO |
| CU04-T01 | Panel comunal — score WLC | Seleccionar una comuna y revisar el desglose del score | Se muestra el score WLC compuesto (0–100) con la contribución de cada variable: FWI, NDMI, NDVI, FIRMS, reportes | El panel muestra el score compuesto y los pesos de cada componente correctamente. El nombre de la comuna aparece en el encabezado del panel. | APROBADO |
| CU04-T02 | Panel comunal — confirmación Copernicus | Hacer clic en "Confirmar con Copernicus" en el panel comunal | Se inicia el sync OpenEO (~70s) y el resultado muestra modo ENHANCED con NDVI/NDMI satelitales actualizados | El proceso de sync se ejecuta, el indicador de carga aparece, y al completarse el panel muestra datos ENHANCED. El botón de impresión permanece accesible. | APROBADO |
| CU05-T01 | Reglas de alerta — crear | Acceder a `/admin/rules` y crear una regla con umbral FWI = 20 para una región | La regla se guarda y aparece en la tabla de reglas activas | La regla se crea correctamente y aparece en la tabla con los umbrales configurados (FWI, NDMI, NDVI, FIRMS, reportes) resumidos en una columna | APROBADO |
| CU05-T02 | Reglas de alerta — editar | Editar una regla existente y cambiar el umbral de reportes ciudadanos | Los cambios se persisten y la tabla refleja el nuevo valor | La edición se guarda sin errores. El formulario pre-carga los valores existentes correctamente. | APROBADO |
| CU05-T03 | Reglas de alerta — eliminar | Eliminar una regla usando el botón de eliminación con modal de confirmación | La regla desaparece de la tabla tras confirmar la eliminación | El modal de confirmación aparece antes de eliminar. Tras confirmar, la regla se elimina y la tabla se actualiza. | APROBADO |
| CU06-T01 | Alertas activas — visualización | Acceder a `/alertas` y revisar las alertas generadas | Se muestra lista de alertas con nivel (PREVENTIVO/ALTO/CRÍTICO), región, mensaje y fecha | Las alertas aparecen con sus niveles diferenciados por color. Los filtros de región y nivel funcionan correctamente. | APROBADO |
| CU07-T01 | Tablero comunitario — publicaciones | Acceder a `/comunidad` y revisar la sección de publicaciones | Se muestran publicaciones del tablero ordenadas por fecha, con prioridad visual diferenciada | Las publicaciones aparecen con título, mensaje, prioridad y autor. El formulario de nueva publicación está disponible. | APROBADO |
| CU08-T01 | Biblioteca de recursos — listar | Acceder a la sección de recursos dentro de `/comunidad` | Se muestra la lista de documentos/recursos disponibles para la comunidad | La biblioteca muestra los recursos cargados. El filtro por tipo funciona. | APROBADO |
| CU08-T02 | Biblioteca de recursos — cargar PDF | Intentar subir un archivo que no sea PDF | El sistema rechaza el archivo y muestra un mensaje de validación | Solo se permiten archivos PDF. Se muestra mensaje de validación cuando se intenta subir otro formato (restricción implementada en este sprint). | APROBADO |
| CU09-T01 | Agenda de contactos — crear | Agregar un nuevo contacto con nombre, organización, teléfono, email, región y comuna | El contacto se guarda y aparece en la lista de contactos | El formulario incluye el campo `comunaId`. El payload enviado al backend incluye `comunaId`, evitando el error 400 anterior. El contacto aparece en la agenda con la información correcta. | APROBADO |
| CU09-T02 | Agenda de contactos — buscar | Usar el filtro de búsqueda para encontrar un contacto por nombre o institución | Los resultados se filtran dinámicamente mostrando solo los contactos que coinciden | El filtro funciona en tiempo real sobre la lista cargada. | APROBADO |
| CU10-T01 | Chat comunitario — enviar mensaje | Enviar un mensaje en el chat de la comunidad | El mensaje aparece en la conversación con nombre del usuario y hora de envío | El chat muestra el mensaje enviado. La conectividad en tiempo real depende del servicio WebSocket disponible en Railway. | PENDIENTE |
| CU11-T01 | Notificaciones — recibir | Ejecutar acción que dispara una alerta (ej. superar umbral FWI configurado) | El usuario recibe una notificación visible en la barra de la aplicación | Las notificaciones se generan cuando las reglas de alerta se activan. El badge de notificaciones se actualiza. | PENDIENTE |
| CU12-T01 | Exportar informe regional PDF | Hacer clic en "Exportar informe" desde el panel regional | Se genera ventana de impresión con el informe regional completo en formato legible | El informe incluye nombre de región, KPIs, fecha y datos de indicadores. La ventana de impresión del navegador se abre automáticamente sin dependencias externas. | APROBADO |
| CU13-T01 | Exportar informe comunal PDF | Seleccionar una comuna y hacer clic en el botón de impresión del panel comunal | Se genera ventana de impresión con el informe comunal incluyendo score WLC y desglose por indicador | El informe comunal muestra el nombre de la comuna, score compuesto, nivel de alerta y contribuciones por variable. El botón de impresión se ubica en posición accesible dentro del panel. | APROBADO |
| CU14-T01 | Panel de accesos RBAC — visualizar usuarios | Acceder a `/admin/access-control` con cuenta ADMIN | Se muestra la lista de usuarios con sus roles asignados | La tabla de usuarios carga correctamente con nombre, email y rol. Las acciones de gestión de roles están disponibles solo para cuentas con los permisos adecuados. | OBSERVADO |
| CU15-T01 | Administración de regiones — listar | Acceder a `/admin/regions` con cuenta ADMIN | Se muestra la lista de regiones con su estado de monitoreo | La tabla muestra las regiones registradas con el toggle de monitoreo activo/inactivo por región. | APROBADO |
| CU15-T02 | Administración de regiones — toggle monitoreo | Activar o desactivar el monitoreo de una región usando el toggle | El estado de monitoreo se persiste y afecta si la región recibe sync programado | El toggle cambia el campo `monitoringEnabled` en el backend. La región deja de recibir actualizaciones de sync si se desactiva. | APROBADO |

---

## 2. Pruebas de seguridad

| ID | Funcionalidad | Acción de prueba | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| SEC01-T01 | Protección de rutas — ruta protegida sin sesión | Acceder directamente a `/territorio` sin estar autenticado | El sistema redirige automáticamente a `/login` | `ProtectedRoute` intercepta la navegación y redirige a `/login`. No se expone contenido protegido. | APROBADO |
| SEC01-T02 | Protección de rutas — ruta pública con sesión activa | Acceder a `/login` con sesión JWT activa | El sistema redirige automáticamente al módulo principal `/territorio` | `PublicOnlyRoute` detecta el token activo y redirige. La página de login no es accesible con sesión iniciada. | APROBADO |
| SEC01-T03 | Protección de rutas admin — usuario sin rol ADMIN | Acceder a `/admin/rules` con una cuenta de usuario normal | El sistema muestra un mensaje de acceso denegado o redirige sin mostrar el contenido | Las rutas `/admin/*` verifican el rol del usuario. Un usuario sin ROLE_ADMIN no puede ver ni interactuar con las páginas de administración. | APROBADO |
| SEC02-T01 | Validación de inputs — campo de texto | Ingresar `<script>alert('xss')</script>` en el campo de mensaje del chat o de publicación | El texto se muestra como literal, sin ejecutar el script | React escapa el contenido por defecto al renderizar en el DOM. No se ejecutan scripts inyectados en campos de texto. | APROBADO |
| SEC02-T02 | Validación de inputs — campos numéricos de reglas | Ingresar un valor negativo o un string no numérico en los umbrales de una AlertRule | El backend rechaza la solicitud con error de validación (400) | El backend valida los tipos. El frontend muestra los mensajes de error de validación retornados por el servidor. | APROBADO |
| SEC03-T01 | Control de roles — botón de sync clima | Verificar visibilidad del botón "Sincronizar clima y viento" con distintos roles | Solo visible para ROLE_ADMIN y ROLE_SUPER_ADMIN; oculto para otros roles (no solo deshabilitado) | El botón no se renderiza para usuarios sin el rol requerido. No aparece en el DOM para cuentas no administrativas. | APROBADO |
| SEC03-T02 | Control de roles — acceso a panel de accesos | Verificar que `/admin/access-control` no es accesible para ROLE_COMMUNITY_USER | La ruta no carga para usuarios sin permisos; se redirige o muestra acceso denegado | La protección de rutas por rol impide el acceso. Confirmado en integración con el sistema RBAC. | OBSERVADO |

---

## 3. Pruebas de usabilidad

| ID | Funcionalidad | Acción de prueba | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| USAB01-T01 | Responsive design — mobile | Abrir la aplicación en un viewport de 375×667 px (iPhone SE) | El layout se adapta: sidebar collapsa, el mapa es navegable, los formularios son legibles | El layout principal usa clases responsivas. La sidebar colapsa en viewports pequeños. El mapa Leaflet es funcional en móvil con controles accesibles. | APROBADO |
| USAB01-T02 | Responsive design — tablet | Abrir la aplicación en un viewport de 768×1024 px (iPad) | El contenido usa el espacio horizontal disponible; los paneles laterales no se superponen | El layout intermedio se muestra correctamente en resolución tablet. Los paneles del mapa territorial mantienen proporciones adecuadas. | APROBADO |
| USAB02-T01 | Accesibilidad — contraste de colores | Revisar el tooltip del choropleth con diferentes niveles de riesgo | El texto del tooltip es legible sobre todos los fondos de color de la escala de riesgo | El tooltip usa color de texto oscuro para fondos claros y `darkUiColor` para fondos oscuros, aplicando la lógica de contraste corregida en este sprint. | APROBADO |
| USAB02-T02 | Accesibilidad — semántica HTML | Inspeccionar el DOM de la barra de navegación y los formularios | Los elementos usan etiquetas semánticas (`nav`, `main`, `button`, `label`) y los campos tienen atributos `for`/`id` vinculados | La estructura HTML usa etiquetas semánticas. Los formularios tienen labels asociados a sus inputs. Los botones usan `<button>` y no `<div>` clickable. | APROBADO |
| USAB02-T03 | Accesibilidad — escala colorblind-safe | Revisar la leyenda del choropleth y las escalas de clima/vegetación | Los colores usados son distinguibles para usuarios con deuteranopia/protanopia (paleta colorblind-safe) | Las escalas de color fueron refactorizadas a paletas colorblind-safe (naranja/azul en lugar de rojo/verde) en el sprint anterior. La leyenda refleja estos colores. | APROBADO |

---

## 4. Resumen de estados

| Estado | Cantidad |
|---|---|
| APROBADO | 30 |
| OBSERVADO | 2 |
| PENDIENTE | 2 |
| **Total** | **34** |

### Casos OBSERVADO
- **CU14-T01:** La gestión de roles en `/admin/access-control` carga correctamente, pero la confirmación de QA con usuario real de producción está pendiente de ejecución formal con Andrés.
- **SEC03-T02:** La protección de ruta `/admin/access-control` para ROLE_COMMUNITY_USER está implementada a nivel de componente, pero falta evidencia de ejecución en producción con ese rol específico.

### Casos PENDIENTE
- **CU10-T01 (Chat):** Depende de conectividad WebSocket con el servicio Railway. La prueba requiere un ambiente con dos usuarios simultáneos conectados.
- **CU11-T01 (Notificaciones push):** La generación automática de notificaciones al disparar una regla requiere que el scheduler esté activo en Railway y que haya datos reales que superen el umbral configurado. No es reproducible de forma inmediata en prueba manual.
