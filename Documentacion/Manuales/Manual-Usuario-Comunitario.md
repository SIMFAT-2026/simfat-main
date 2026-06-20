# Manual de Usuario — Rol: Usuario Comunitario

**Proyecto:** SIMFAT-2026 — Sistema de Monitoreo y Alerta Temprana Forestal
**Proyecto de título — Duoc UC**, Escuela de Informática y Telecomunicaciones, Sección 002D
**Docente guía:** Arturo Vargas

| Rut | Nombre | Correo |
|---|---|---|
| 18.239.964-7 | Andrés Ibáñez Rojas | and.ibanezr@duocuc.cl |
| 18.832.438-k | David Vásquez Ovalle | davi.vasquezo@duocuc.cl |

## Histórico de revisiones

| Versión | Fecha | Descripción/cambio | Autor |
|---|---|---|---|
| 1.0 | 10/06/2026 | Manual de usuario inicial | Andrés Ibáñez |
| 2.0 | 20/06/2026 | Actualización del manual | Andrés Ibáñez |
| 3.0 | 20/06/2026 | Revisión técnica contra código fuente, sin hallazgos de error | David Vásquez |

---

## 1. Objetivo

Empoderar a las comunidades para usar SIMFAT como herramienta de vigilancia ciudadana. El usuario aprenderá a navegar el mapa interactivo, interpretar indicadores de salud vegetal y generar reportes sobre el estado de sus regiones.

Este manual sigue el estándar institucional del proyecto. SIMFAT utiliza tecnología satelital de la red Copernicus, procesada a través de openEO.

## 2. Consideraciones técnicas

- **Acceso:** requiere una cuenta registrada (proceso de Registro/Login).
- **Geolocalización:** se recomienda activar permisos de ubicación para centrar el mapa en la región del usuario.
- **Datos en tiempo real:** la información proviene de satélites Sentinel-2; la frescura de los datos puede consultarse en el panel.

## 3. Creación de cuenta y registro

Para participar del monitoreo y generar reportes es necesario crear una cuenta personal.

**Pasos para el registro:**

1. **Acceso:** en la pantalla de inicio de sesión, hacer clic en "Crear Cuenta".
2. **Formulario de datos:**
   - **Nombre Completo:** para identificación en los reportes.
   - **Correo Electrónico:** dirección válida donde se recibirán notificaciones y enlaces de recuperación.
   - **Contraseña:** debe cumplir los requisitos de seguridad.
3. **Seguridad de la contraseña:** la plataforma muestra en tiempo real:
   - **Indicador de seguridad:** débil, media o fuerte.
   - **Requisitos visibles:** caracteres mínimos y símbolos necesarios.
   - **Visualización:** ícono de "ojo" para mostrar/ocultar la contraseña.
4. **Finalización:** al hacer clic en "Registrarse", si los datos son válidos el sistema confirma el éxito y redirige a la pantalla de Inicio de Sesión.

## 4. Guía de uso del mapa interactivo

El mapa permite visualizar la salud del bosque mediante dos indicadores clave:

- **NDVI (Índice de Vegetación):** muestra el verdor y densidad del bosque. Valores cercanos a 1 indican vegetación sana.
- **NDMI (Índice de Humedad):** identifica zonas con estrés hídrico o sequedad, factores críticos para el riesgo de incendios.

**Cómo usarlo:**

1. **Selección de Región:** elegir la región para enfocar el monitoreo.
2. **Capas de Información:** alternar entre las vistas de NDVI y NDMI.
3. **Historial:** usar la barra temporal para ver la evolución del bosque en los últimos 30 días o por semanas.

## 5. Generación de Reportes Comunitarios

SIMFAT permite que el usuario comunitario actúe como un sensor humano en el territorio mediante la Creación de Reportes Ciudadanos. Ante cualquier anomalía evidenciada físicamente — focos de incendio incipientes, talas no autorizadas — el ciudadano puede generar un reporte detallado desde la interfaz de la comunidad.

El formulario de "Nuevo reporte" solicita:

- **Región** y **Comuna**.
- **Categoría** (ej. HUMO, QUEMA, OTRO).
- **Latitud / Longitud** (o el botón "Usar mi ubicación").
- **Descripción** de la situación observada.
- **Fotos:** máximo 4 archivos, 5 MB cada uno, como evidencia visual (almacenadas mediante Supabase Storage).

Los reportes quedan disponibles en "Seguimiento de reportes" con su estado (Recibido / Validado) y se almacenan en el sistema de persistencia dual de la plataforma, complementando las alertas satelitales con validación en tiempo real desde el terreno.

## 6. Participación y Monitoreo de "Frescura"

- **Estado de Datos (Data Freshness):** indica si los datos están "FRESH" (actualizados) o "STALE" (antiguos); si están desactualizados, se puede solicitar una sincronización.
- **Reportes de Alerta:** el usuario puede usar la información de la plataforma para generar reportes externos o avisar a las autoridades locales en base a las Alertas Tempranas del sistema.

## 7. Gestión de Perfil y Seguridad

- **Privacidad:** gestión de datos personales y cierre de sesión seguro para proteger la trazabilidad de los reportes.
- **Recuperación de Acceso:** restablecimiento de contraseña por correo electrónico en caso de olvido (enlace válido por 30 minutos, un solo uso).
