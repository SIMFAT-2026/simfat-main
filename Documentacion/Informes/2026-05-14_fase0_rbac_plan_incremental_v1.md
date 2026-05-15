# Fase 0 - Plan Incremental de Implementacion RBAC + JWT

- Fecha: 2026-05-14
- Version: 1.0

## Objetivo del plan

Implementar RBAC de forma incremental, controlada y trazable, manteniendo continuidad operativa del backend y frontend.

## Fases

1. Fase 0 (actual)
- Diseno de arquitectura, contrato y QA documental.

2. Fase 1
- Introducir entidades relacionales de roles/permisos en PostgreSQL.
- Introducir metadatos de verificacion y trazabilidad.

3. Fase 2
- Integrar resolucion de permisos en Spring Security.
- Mantener JWT compatible y agregar estrategia de invalidacion por version.

4. Fase 3
- Aplicar proteccion por endpoint de manera escalonada.
- Ejecutar regresion frontend/backend tras cada bloque.

5. Fase 4
- Agregar auditoria de acciones privilegiadas.
- Iniciar base para trust score/reputacion.

## Politica de despliegue incremental

- Primero ambientes locales y QA.
- Luego integracion remota con pruebas de humo.
- Despliegue en lotes con monitoreo de 401/403 y rollback rapido.

## Gate de avance entre fases

Una fase no avanza sin:
- checklist QA de fase anterior en estado conforme,
- evidencia de pruebas publicadas,
- riesgos residuales declarados.
