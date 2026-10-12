# Field Notes Backend — Offline Synchronization & Hardening Report

## Executive Summary

This report documents the implementation of the offline-first delta synchronization system (Phase 9), the mandatory migration to the custom API base path `/api/fieldnotesatl`, the database schema expansion (Flyway V3), and the automated verification suite for the Field Notes backend.

---

## 1. Current Backend Architecture

The backend is built as a modular Spring Boot 3.5 / Java 21 REST API following clean architecture principles:

- **Web / API Layer**: Controllers mapped under `/api/fieldnotesatl/*`, using DTOs, Bean Validation (`jakarta.validation`), and OpenAPI documentation (`springdoc`).
- **Security Layer**: Spring Security with stateless session management, BCrypt password hashing, custom `JwtAuthenticationFilter` verifying HMAC-SHA256 tokens, and database-backed refresh tokens with revocation support.
- **Service Layer**: Transactional business services enforcing tenant/user data isolation, optimistic version comparisons, and dependency-ordered write operations.
- **Persistence Layer**: Spring Data JPA / Hibernate mapped to PostgreSQL 16 hosted on Neon, managed via Flyway versioned migrations (`V1`, `V2`, `V3`). Hibernate `ddl-auto` is strictly set to `validate` in production to prevent schema drift.

### Package Layout
```
com.fieldnotes
├── auth          # Registration, login, token refresh, revocation
├── common        # Exceptions, global handler, PageResponse, ApiError
├── config        # SecurityConfig, CorsConfig, OpenAPI config
├── customer      # Customers entity, repository, service, controller
├── fieldnote     # Field notes entity, repository, service, controller
├── site          # Sites entity, repository, service, controller
├── sync          # Offline sync DTOs, entity, repository, service, controller
└── user          # User entity, repository, user details service
```

---

## 2. Required Environment Variables & Configuration

The application reads runtime configuration from environment variables with safe defaults for local development:

| Variable | Description | Required? | Default / Example |
| :--- | :--- | :--- | :--- |
| `DB_URL` | JDBC URL for PostgreSQL / Neon | Yes (Prod) | `jdbc:postgresql://localhost:5432/field_notes` |
| `DB_USERNAME` | Database username | Yes (Prod) | `fieldnotes` |
| `DB_PASSWORD` | Database password | Yes (Prod) | `fieldnotes` |
| `JWT_SECRET` | Secret key for HMAC token signing (>= 32 bytes) | **Yes** | *Must be provided in .env* |
| `JWT_EXPIRATION_MS` | Access token lifespan in milliseconds | No | `3600000` (1 hour) |
| `SERVER_PORT` | HTTP port | No | `8080` |
| `CORS_ALLOWED_ORIGINS` | Permitted origins for web/mobile | No | `http://localhost:3000,http://localhost:8081` |

---

## 3. Neon Database Connection Setup

To connect the backend to Neon Serverless PostgreSQL:
1. In your Neon console, create a database named `field_notes` and copy the pooled JDBC connection string.
2. In your `.env` file (never committed to git), configure:
   ```env
   DB_URL=jdbc:postgresql://<neon-endpoint>.pooler.neon.tech:5432/field_notes?sslmode=require
   DB_USERNAME=<neon-user>
   DB_PASSWORD=<neon-password>
   JWT_SECRET=<32-character-or-longer-random-secret>
   ```
3. Flyway migrations (`V1`, `V2`, `V3`) run automatically on application startup to initialize the relational schema, constraints, indexes, and cursor table.

---

## 4. Docker Build & Run Instructions

> [!NOTE]
> Docker is not installed on this host environment. The `Dockerfile` and `compose.yaml` files have not been built or executed on this machine and remain unverified.

### Multi-stage Dockerfile
The project includes a two-stage Dockerfile (`eclipse-temurin:21` JRE):
1. **Build Stage**: Compiles and packages the JAR with Maven using Java 21 Temurin.
2. **Runtime Stage**: Copies only the built JAR to a minimal Jammy JRE image, exposing port 8080.

### Running with Docker Compose
```bash
# 1. Ensure .env has JWT_SECRET set
cp .env.example .env

# 2. Start PostgreSQL and Field Notes Backend
docker compose up -d --build

# 3. Check container logs
docker compose logs -f backend

# 4. Verify healthcheck endpoint
curl http://localhost:8080/actuator/health
```

---

## 5. Custom API Base Path: `/api/fieldnotesatl`

All API endpoints are mounted under `/api/fieldnotesatl`:

- **Auth**: `/api/fieldnotesatl/auth/**` (register, login, refresh, logout)
- **Customers**: `/api/fieldnotesatl/customers/**`
- **Sites**: `/api/fieldnotesatl/sites/**`
- **Field Notes**: `/api/fieldnotesatl/field-notes/**`
- **Offline Sync**: `/api/fieldnotesatl/sync`

---

## 6. Offline Synchronization Contract

### Endpoint: `POST /api/fieldnotesatl/sync`

Executes within a single atomic transaction (`@Transactional`). Both the pushed changes and the delta pull query run together in one database transaction; changes are committed at transaction completion:

### Push Requirements:
1. **Stable Identifiers**: Client sends pre-generated UUIDs for new or existing records.
2. **Dependency Ordering**: Writes are processed in dependency order: Customers first, then Sites (validating Customer belongs to user and is not deleted), then Field Notes (validating Site belongs to user and is not deleted).
3. **Idempotency & Retry**: Pushing an identical payload again (e.g. after a client timeout) is treated as already applied. No duplicate rows are created, no spurious version increments occur, and `conflicts` is empty.
4. **Soft-Delete Cascades**:
   - Deleting a customer cascades soft-deletes to all its sites and field notes.
   - Deleting a site cascades soft-deletes to all its field notes.
   - All cascaded children emit tombstones with incremented version counters.
   - Both `SyncService` and REST controllers (`CustomerController`, `SiteController`) enforce identical cascade behavior.

### Pull Requirements:
1. **Database Clock & Overlap Window**:
   - `newCursor` is captured using the database clock (`SELECT CURRENT_TIMESTAMP`) before pull queries run.
   - Pull queries use an inclusive 2-second overlap window (`since.minusSeconds(2)`) before the previous cursor to prevent missed records caused by concurrent transaction commits or clock boundaries.
2. **Cursor Precedence**:
   - The server-stored `DeviceSyncCursor` is authoritative.
   - A client-supplied `lastSyncAt` is only honored if it is strictly EARLIER than the stored cursor (allowing intentional historical re-pulls). Future or later timestamps cannot skip data.
3. **Tombstones**: Returns UUIDs of soft-deleted records in `deletedCustomerIds`, `deletedSiteIds`, `deletedFieldNoteIds`.

### Conflict Resolution Policy:
- **Server Version Ahead**: If `server.version > client.version` and content differs, the write is rejected and a `SyncConflict` object is returned in `conflicts[]`. The server record is returned in the pull payload so the client can present a resolution prompt.
- **Parent Deleted**: If client pushes an entity whose parent is soft-deleted, a conflict is returned.
- **Client Version >= Server Version**: Last-write-wins; changes applied and version incremented.

---

## 7. Automated Testing Results

### Summary of Executed Tests

All 46 integration and contract tests pass with 0 errors and 0 failures:

| Test Class | Tests Run | Result | Focus |
| :--- | :---: | :---: | :--- |
| `ApplicationConfigurationTest` | 1 | PASS | Spring context loading and configuration validation |
| `MigrationSqlContractTest` | 3 | PASS | V1, V2, and V3 Flyway migration SQL contract checks |
| `AuthControllerTest` | 9 | PASS | Registration, login, JWT validation, refresh, logout |
| `CustomerControllerTest` | 6 | PASS | Customer CRUD, search, pagination, tenant isolation |
| `SiteControllerTest` | 6 | PASS | Site CRUD, parent validation, cross-user security |
| `FieldNoteControllerTest` | 6 | PASS | Note CRUD, geo/photo metadata, filtering, ownership |
| `SyncControllerTest` | 15 | PASS | Delta sync, DB cursor, 2s overlap race prevention, idempotency, soft-delete cascades, parent-deleted conflicts, long-absence tombstones, cursor precedence |
| **Total** | **46** | **100% PASS** | **0 Failures, 0 Errors, 0 Skipped** |

### How to Run Automated Tests
```bash
mvn test
```

---

## 8. Verification Matrix: Verified vs. Unverified

| Check | Status | Evidence / Notes |
| :--- | :---: | :--- |
| API Base Path `/api/fieldnotesatl` | **VERIFIED** | All controllers, configs, and 46 tests use `/api/fieldnotesatl`. |
| Relational Schema & Migrations | **VERIFIED** | V1, V2, and V3 Flyway migrations verified via SQL contract tests. |
| Sync API & Conflict Logic | **VERIFIED** | 15 integration tests in `SyncControllerTest` passed. |
| DB Clock & 2s Overlap Window | **VERIFIED** | Verified with race condition test in `SyncControllerTest`. |
| Idempotency & Retry Behavior | **VERIFIED** | Verified identical payload push returns 0 conflicts and current record. |
| Soft-Delete Cascades | **VERIFIED** | Verified customer deletion cascades tombstones to sites and notes. |
| Cursor Precedence Rules | **VERIFIED** | Verified re-pull with earlier cursor and prevention of skip with future cursor. |
| Security & Tenant Isolation | **VERIFIED** | Cross-tenant access rejected (401/403) across all resources. |
| Maven Compilation & Build | **VERIFIED** | Clean build with Maven 3.9.9 / Java 21 (`BUILD SUCCESS`). |
| Real PostgreSQL via Testcontainers | **UNVERIFIED** | Docker is not installed on this machine (`docker: command not found`). |
| Docker Build / Daemon Run | **UNVERIFIED** | Docker daemon is not available locally. Not built or run. |
| Live Neon PostgreSQL Connection | **UNVERIFIED** | Live Neon credentials not configured in host environment. |
