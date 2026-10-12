# Implementation Roadmap & Phase Status

## Phase 1 — Architecture and Review [COMPLETED]
- Confirm user ownership model and security assumptions.
- Review schema and endpoint design.
- Approve architecture and migration strategy.

## Phase 2 — Project Foundation [COMPLETED]
- Create Spring Boot project skeleton (Java 21, Spring Boot 3.5).
- Configure PostgreSQL, Flyway, security, and OpenAPI docs.
- Define DTOs, enums, and base response types.

## Phase 3 — Core Security [COMPLETED]
- Implement users and auth domain (`/api/fieldnotesatl/auth/**`).
- BCrypt password encoder & stateless session management.
- JWT access tokens (HMAC-SHA) and refresh tokens with database tracking & revocation.
- Security permit list for public routes, strict authentication on protected routes.

## Phase 4 — Customer Domain [COMPLETED]
- Customer entity, repository, service, and controller (`/api/fieldnotesatl/customers`).
- Pagination, search filtering, and strict user ownership checks.

## Phase 5 — Site Domain [COMPLETED]
- Site entity and repository with customer foreign keys (`/api/fieldnotesatl/sites`).
- Cross-user ownership validation preventing cross-tenant customer linkage.

## Phase 6 — Field Note Domain [COMPLETED]
- FieldNote entity, repository, status enum (`PENDING`, `IN_PROGRESS`, `COMPLETED`).
- Geo coordinates, photo reference string, noteDate.
- Filtering by customer, site, and status (`/api/fieldnotesatl/field-notes`).

## Phase 7 — Schema Hardening & Soft Deletes [COMPLETED]
- Flyway V2: Added `deleted_at`, `version`, update timestamp indexes, and restrictive foreign keys (`ON DELETE RESTRICT`).
- Soft-delete semantics across all resources to protect historical field records.

## Phase 8 — API Path Migration [COMPLETED]
- Migrated all API routes from generic `/api/v1` to the required custom base path `/api/fieldnotesatl`.
- Updated SecurityConfig permit matchers, Swagger/OpenAPI annotations, and test suite.

## Phase 9 — Offline Synchronization [COMPLETED]
- Flyway V3: Created `device_sync_cursors` table with unique constraint on `(user_id, device_id)` and foreign key cascade on user delete.
- Implemented `DeviceSyncCursor` entity and `DeviceSyncCursorRepository`.
- Implemented push-then-pull synchronization logic in `SyncService`:
  - Upsert semantics with client-generated UUIDs.
  - Dependency order processing (customers → sites → field notes).
  - Version-based optimistic conflict detection returning `SyncConflict` payloads.
  - Tombstone tracking for soft-deleted entities returned in pull response.
  - Watermark cursor tracking per `(user, device)` pair.
  - Idempotent request handling for safe retry after network drops.
- Exposed endpoint `POST /api/fieldnotesatl/sync`.
- Full integration test suite `SyncControllerTest` (9 tests passing).

## Phase 10 — Release Readiness & Verification [IN PROGRESS]
- Complete automated test suite: 40 tests passing (100% pass rate).
- Contract verification of V1, V2, and V3 Flyway migration scripts.
- Docker configuration (`Dockerfile`, `compose.yaml`) validated.
- Architecture and API documentation updated.
