# Manual de Usuario — Rol: Administrador

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
| 3.0 | 20/06/2026 | Corrección de flujo de creación de administradores y precisiones técnicas tras auditoría contra código fuente | David Vásquez |

---

## 1. Objetivo

Este manual guía al Administrador en el uso del ecosistema SIMFAT-2026 para el monitoreo forestal: gestión de alertas, visualización de indicadores satelitales (NDVI/NDMI) y administración de la sincronización de datos con el servicio openEO.

## 2. Usuarios involucrados

**Administrador:** usuario con privilegios para supervisar el estado de las regiones, gestionar la configuración de Áreas de Interés (AOI) y forzar la actualización de datos satelitales para mantener vigente la información del Dashboard.

## 3. Consideraciones técnicas

- **Conectividad:** requiere conexión a internet para interactuar con el backend y recibir datos de los servicios satelitales de Copernicus.
- **Seguridad:** autenticación mediante tokens JWT (Access y Refresh). El Access Token tiene una vigencia de 15 minutos; el Refresh Token, 14 días.
- **Compatibilidad:** interfaz construida en React + Vite, optimizada para navegadores modernos.

## 4. Ingreso al sistema

### 4.1 Cómo se otorgan privilegios de Administrador

El rol de Administrador **no se crea desde un formulario propio**: se asigna sobre una cuenta que ya existe. El flujo real es:

1. La persona se registra como cualquier usuario, desde la pantalla pública `/register` ("Crear cuenta").
2. Un Administrador o Super Administrador ya existente ingresa al panel **Control de Accesos** (`/admin/access-control`, etiquetado "Accesos" en el menú).
3. Desde ese panel busca la cuenta y le asigna el perfil **Administrador** o **Super Administrador** mediante un selector de rol.

No existe una sección "Crear Administradores" con campos propios de nombre/correo/contraseña dentro del panel admin — la creación de la cuenta y la asignación del rol son dos pasos separados.

**Propósito:** delegar responsabilidades de gestión (configurar regiones, supervisar alertas globales, gestionar la sincronización de datos con openEO) sin necesidad de que el equipo técnico cree cuentas manualmente en la base de datos.

### 4.2 Inicio de sesión

El acceso se realiza con Correo Electrónico y Contraseña.

- **Validación:** el sistema verifica las credenciales contra PostgreSQL utilizando hashing BCrypt.
- **Error de ingreso:** si los datos son inválidos, se muestra un mensaje de error estandarizado según el contrato `ApiResponse`.

## 5. Vista del Administrador: Panel de Control (Dashboard)

El Dashboard es la interfaz principal donde el administrador monitorea la salud forestal:

- **Resumen de Alertas:** alertas tempranas activas en tiempo real.
- **Regiones Críticas:** zonas con mayor pérdida de vegetación detectada.
- **Tendencia de Pérdida:** evolución histórica del deterioro forestal.

## 6. Gestión de Datos y Sincronización

Sección exclusiva del administrador para interactuar con el openeo-service:

- **Sincronización Manual:** dispara la actualización de datos para una región específica (`POST /api/dashboard/sync/run`, con parámetros opcionales `regionId`, `from`, `to`).
- **Monitoreo de Indicadores:** valores de NDVI (vegetación) y NDMI (humedad) procesados desde Sentinel-2.
- **Frescura de Datos:** consulta de cuándo se actualizaron por última vez los datos de una región (`GET /api/dashboard/data-freshness?regionId={id}` — el parámetro de región es obligatorio).

## 7. Configuración de Regiones y AOI

- **Cobertura de AOI:** revisar qué regiones cuentan con coordenadas Bbox (West, South, East, North) válidas para la consulta satelital.
- **Actualización de Coordenadas:** modificar los límites geográficos de las regiones para ajustar el monitoreo satelital.

> El catálogo de regiones del sistema cubre las 16 regiones oficiales de Chile, pero el monitoreo satelital activo (NDVI/NDMI/FIRMS/FWI) opera en la práctica sobre las regiones piloto: Ñuble, Biobío y La Araucanía.

## 8. Gestión de Perfil y Sesión

- **Recuperar Contraseña:** se puede solicitar un enlace de restablecimiento por correo (válido por 30 minutos, un solo uso).
- **Cerrar Sesión:** invalida los tokens en el cliente y redirige al Login.
