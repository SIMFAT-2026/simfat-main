## Testing Capabilities

**Strict TDD Mode**: disabled (project-wide); backend alone has real test infrastructure, frontend has none
**Detected**: 2026-06-28

### Backend — Producto/backend/simfat-backend (Java 17, Spring Boot 3.3.4, Maven)

#### Test Runner

- Command: `mvn test` (run from `Producto/backend/simfat-backend`)
- Framework: JUnit 5 via `spring-boot-starter-test`

#### Test Layers

| Layer       | Available | Tool                                                         |
| ----------- | --------- | ------------------------------------------------------------- |
| Unit        | Yes       | JUnit 5, Mockito (via spring-boot-starter-test)                |
| Integration | Yes       | `@SpringBootTest`/`WebApplicationFactory`-style controller tests, embedded Mongo (`de.flapdoodle.embed.mongo`), H2 (JPA/Postgres tests), MockWebServer (external HTTP), `spring-security-test` |
| E2E         | No        | —                                                               |

22 existing test classes found under `src/test/java`, covering controllers, services, repositories, and model converters (e.g. `AuthServiceImplTest`, `TerritoryControllerClimateIntegrationTest`, `OpenEoSyncServiceImplTest`, `ComunaRiskSnapshotRepositoryIntegrationTest`).

#### Coverage

- Available: No (no JaCoCo or equivalent plugin in `pom.xml`)
- Command: —

#### Quality Tools

| Tool         | Available | Command |
| ------------ | --------- | ------- |
| Linter       | No (no Checkstyle/Spotless found) | — |
| Type checker | N/A (statically typed Java) | — |
| Formatter    | No | — |

### Frontend — Producto/frontend/simfat-web (React 18 + Vite)

#### Test Runner

- Command: none — **no test script exists** in `package.json` (`scripts` only has `dev`, `build`, `preview`, `lint`)
- Framework: none installed (no vitest, jest, @testing-library/*, cypress, or playwright in `dependencies`/`devDependencies`)

#### Test Layers

| Layer       | Available | Tool |
| ----------- | --------- | ---- |
| Unit        | No        | —    |
| Integration | No        | —    |
| E2E         | No        | —    |

No `*.test.{js,jsx,ts,tsx}` files found anywhere in the frontend tree. No `tsconfig.json` despite a handful of `.tsx` files (e.g. `DashboardPage.tsx`) coexisting with JSX — TypeScript is not actually configured/enforced.

#### Coverage

- Available: No
- Command: —

#### Quality Tools

| Tool         | Available | Command |
| ------------ | --------- | ------- |
| Linter       | Yes       | `npm run lint` (`eslint . --ext js,jsx --max-warnings 0`) |
| Type checker | No        | — (no tsconfig, no `tsc` script) |
| Formatter    | No        | — (no Prettier config found) |

### Strict TDD Decision

Per the decision gate (no test runner → `strict_tdd: false` with explanation), this is a **mixed-capability monorepo**: the backend genuinely supports TDD today (real JUnit suite, multiple test doubles), but the frontend has zero test infrastructure. Setting a single project-wide `strict_tdd: true` would be misleading for any frontend-touching change. Decision: `strict_tdd: false` at the project level.

Recommendation for future `sdd-apply`/`sdd-verify` runs: treat TDD as backend-only and opt-in per change — when a change is backend-only, the executor MAY apply RED-GREEN-REFACTOR using `mvn test`; when a change touches the frontend, no test command can be required until a frontend test runner (e.g. Vitest + Testing Library) is introduced.
