# Modelo Logico Backend - Actualizacion RBAC (Fase 0)

- Fecha: 2026-05-14
- Version: 1.0

## Objetivo

Extender el modelo logico de backend para incorporar control de acceso robusto y verificaciones avanzadas sin romper los contratos actuales de autenticacion.

## Capas logicas

1. Autenticacion
- Continua basada en JWT (`access` + `refresh`).
- Flujo login/refresh/logout sin cambios funcionales en Fase 0.

2. Autorizacion
- Se desacopla de un enum corto (`USER/ADMIN`).
- Se migra a RBAC relacional con permisos declarativos.

3. Verificacion y confianza
- Estado de verificacion separado de rol.
- Base para trust score y reputacion.

4. Dominio operacional
- MongoDB conserva datos de negocio (reportes, comunidad, alertas).
- PostgreSQL concentra identidad, sesion, rol y permisos.

## Reglas logicas clave

- Un usuario puede tener multiples roles.
- Un rol puede mapear multiples permisos.
- Un permiso puede ser compartido por multiples roles.
- El estado de verificacion no reemplaza rol; lo complementa.
- La autorizacion de endpoints debe consultar permisos efectivos.

## Estado de avance

- Fase 0: diseno y contrato listos.
- Fase 1+: pendiente implementacion incremental.
