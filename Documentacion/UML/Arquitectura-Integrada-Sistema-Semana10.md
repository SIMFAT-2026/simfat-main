# Arquitectura Integrada SIMFAT - Semana 10

Fecha: 2026-05-15

## Diagrama de arquitectura (capas y flujo)

```mermaid
flowchart TB
  subgraph UI[Frontend React/Vite]
    Pages[Paginas y modulos]
    AuthCtx[AuthContext + rutas protegidas]
    AccessUI[Panel Control de Accesos]
  end

  subgraph API[Backend Spring Boot]
    Controllers[Controllers REST]
    Security[Spring Security + JWT Filter]
    RBAC[AuthorizationResolver + @PreAuthorize]
    Services[Servicios de negocio]
    Audit[Audit Filter acciones privilegiadas]
  end

  subgraph DATA[Persistencia]
    PG[(PostgreSQL/Supabase)]
    MDB[(MongoDB Atlas)]
  end

  subgraph EXT[Integraciones]
    OpenEO[OpenEO/NASA]
    SupaStorage[Supabase Storage]
  end

  Pages --> AuthCtx --> API
  AccessUI --> API
  API --> Security --> RBAC --> Controllers --> Services
  Services --> PG
  Services --> MDB
  Services --> OpenEO
  Services --> SupaStorage
  Security --> Audit
```

## Flujo de seguridad

1. El usuario autentica y obtiene JWT.
2. Cada request protegido pasa por `JwtAuthenticationFilter`.
3. El backend resuelve roles/permisos efectivos desde BD.
4. `@PreAuthorize` aplica minimo privilegio en endpoints criticos.
5. Acciones privilegiadas mutantes se registran en auditoria.
