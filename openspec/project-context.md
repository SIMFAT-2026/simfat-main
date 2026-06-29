# SDD Project Context — simfat-main

**Initialized**: 2026-06-28
**Persistence mode**: openspec (file-based; Engram MCP unreachable this session — see Risks)

## Stack

- Monorepo with two independently versioned apps:
  - `Producto/backend/simfat-backend` — Java 17, Spring Boot 3.3.4, Maven.
    - Persistence: dual — MongoDB (Spring Data Mongo) **and** PostgreSQL via Spring Data JPA + Flyway migrations. Not Mongo-only.
    - Auth: Spring Security + JWT (`jjwt` 0.12.6), dual RBAC (legacy + modern roles per project memory).
    - API docs: springdoc-openapi.
  - `Producto/frontend/simfat-web` — React 18.3, Vite 8, React Router 6, Leaflet/react-leaflet (maps), Recharts (charts), Axios.
    - Mixed JS/JSX with some `.tsx` files (e.g. `DashboardPage.tsx`) but **no `tsconfig.json`** — TypeScript is not actually configured.

## Testing

See `openspec/testing-capabilities.md` for full detail. Summary:

- Backend: real JUnit 5 test suite (22 test classes), integration tests with embedded Mongo + H2 + MockWebServer. `mvn test` works.
- Frontend: no test runner, no test framework, no test files at all. Only `npm run lint` (ESLint) exists as a quality gate.
- `strict_tdd: false` at project level (mixed capability — see decision rationale in testing-capabilities.md).

## Conventions Observed

- No project-level `AGENTS.md`/`CLAUDE.md`/`.cursorrules` found at repo root.
- No project-level skills directory found (`.claude/skills`, `skills/`, etc. all absent at project root).
- Recent commit history shows conventional-commit-style messages with scopes, e.g. `fix(territory): ...`, `feat(territory): ...`, `perf(territory): ...`, `chore(territory): ...`.

## Current Branch Context

- Branch: `feature/firms-comuna-geo-attribution`, created off `main`.
- Recent work centers on the `territory` module: FIRMS/FWI reporting, comunal risk score aggregation/legend, auto-escalation rules for FIRMS detections.
