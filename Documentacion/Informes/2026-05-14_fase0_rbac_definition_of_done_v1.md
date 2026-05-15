# Fase 0 - Definition of Done (DoD) RBAC + JWT

- Fecha: 2026-05-14
- Version: 1.0
- Estado: Vigente para sprint RBAC

## DoD general de la capa RBAC

1. Diseno aprobado
- Contrato tecnico de roles/permisos documentado y versionado.
- Matriz endpoint-permiso revisada por backend y QA.

2. Seguridad
- Endpoints criticos con regla explicita de autorizacion.
- Sin `permitAll` en operaciones de escritura administrativa.
- Principio de minimo privilegio aplicado y evidenciado.

3. JWT y sesion
- JWT mantiene compatibilidad con login/refresh/logout.
- Manejo de cambios de rol sin sesion huerfana (estrategia definida).

4. Datos
- Estructura de roles y permisos persistida con migraciones versionadas.
- Estrategia de migracion de datos legacy documentada.

5. Calidad
- Pruebas unitarias e integracion de seguridad ejecutadas.
- Casos negativos de autorizacion cubiertos (401/403).

6. Evidencia
- Checklist QA actualizado con estado `OK/Pendiente/No aplica`.
- Evidencia de pruebas y hallazgos publicada en `Documentacion/Evidencias`.

7. Operacion
- Plan de rollback definido para cambios de seguridad.
- Riesgos conocidos registrados en informe de iteracion.

## Criterio de cierre de Fase 0

Fase 0 se considera cerrada cuando toda la documentacion base esta publicada, enlazada desde README y validada por QA documental.
