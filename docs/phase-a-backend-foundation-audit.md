# Phase A: Backend Foundation Audit

## Scope

This document records the first implementation phase for the Field Notes backend. The goal was to inspect the existing scaffold, fix the real foundation issues, align the project to a stable supported version, correct the initial database design for offline-friendly sync, and verify the work using the toolchain actually available in this environment.

The work here is intentionally limited to foundation work. It does not claim the backend is feature-complete or production-ready.

## Current truth check (revalidated)

I re-checked the actual repository state and the current status is:

- Phase A foundation work is complete and valid: Java 21 + Spring Boot 3.5.16, the security startup issue is fixed, the schema is corrected for sync-safe deletions, and the project builds cleanly in the available environment.
- The project is not fully finished. Authentication endpoints, CRUD APIs, sync logic, photo storage, and production deployment remain unimplemented.
- The backend is in a clean starting state for Phase B, but not yet ready to claim the full Field Notes backend is complete.

## Source of truth and project intent

The assignment requirements are the original Field Notes task description: users register and log in, manage customers, add sites under customers, create field notes for sites, work offline, synchronize later, and restore state after reinstall or data loss. The backend is a lightweight modular monolith in Java + Spring Boot, using PostgreSQL, Flyway, JWT, and Docker.

The Flutter app is separate and should use a single configured API base URL for backend calls. The database is not exposed to the app; Neon is the production database host, while a Dockerized PostgreSQL instance is used for local development.

## Files inspected

The following areas were reviewed before change:

- Maven build definition: [pom.xml](../pom.xml)
- Runtime configuration: [application.yml](../src/main/resources/application.yml) and [application-dev.yml](../src/main/resources/application-dev.yml)
- Initial migration: [V1__initial_schema.sql](../src/main/resources/db/migration/V1__initial_schema.sql)
- Java source and tests: [src/main/java](../src/main/java) and [src/test/java](../src/test/java)
- Docker runtime files: [Dockerfile](../Dockerfile), [compose.yaml](../compose.yaml), [.env.example](../.env.example), and [.gitignore](../.gitignore)
- Architecture and planning docs: [docs](../docs)

## Problems identified

1. Version inconsistency
   - Documentation and previous planning described Java 25 + Spring Boot 3.5.16.
   - The actual build configuration had to be reconciled with the available runtime and a stable supported baseline.

2. Security startup defect
   - Spring Security was configured with an `AuthenticationManager` that depended on `UserDetailsService`.
   - No `UserDetailsService` bean existed in the project, so the application context failed to start.

3. Unsafe default configuration
   - The default JWT secret was a weak placeholder value and should not be used in real startup.
   - The app also configured an in-memory default admin user in the security config, which is not suitable for production-grade backend behavior.

4. Relationship design did not support sync-safe delete semantics
   - The original database design used cascading deletes for resource relationships.
   - That is incompatible with the requirement to maintain tombstones for offline deletes and synchronization-safe retry logic.

5. Loss of client-generated UUID safety
   - The schema allowed generated database UUIDs but did not clearly preserve client-supplied IDs in the migration strategy.
   - The design must tolerate offline-created IDs from the Flutter app.

6. Tooling environment mismatch
   - Maven was present but not on PATH in the initial command execution; it was available at a known installation path.
   - Docker and PostgreSQL client tools were not installed in the current environment, preventing container and database runtime validation.

## Decisions made

### Java and Boot version

- Final version chosen: Java 21 + Spring Boot 3.5.16
- Reason: Java 21 is the supported LTS runtime available in this environment, and Spring Boot 3.5.16 resolves and runs cleanly on it.
- This version choice is now consistent across the project configuration and documentation.

### Synchronization-ready schema

A forward-only migration was added:

- [V2__add_sync_tombstones_and_restrict_deletes.sql](../src/main/resources/db/migration/V2__add_sync_tombstones_and_restrict_deletes.sql)

This migration adds:

- `deleted_at` to customers, sites, and field notes
- `version` as a nonnegative revision counter for optimistic conflict detection
- restrictive foreign keys instead of cascading deletes for the main ownership relationships
- update-order indexes for efficient sync and ownership checks

This preserves a clean and minimal sync-ready foundation without rewriting V1.

### UUID handling

The schema keeps UUID defaults as a fallback, but the chosen design supports client-generated IDs as the primary path for Flutter offline work. The API and sync logic still need to enforce idempotent retry handling, conflict detection, and version increments—those are future implementation tasks.

### Ownership and delete semantics

Resource ownership will remain anchored to the authenticated user, and database-level enforcement will be consistent with the parent-child relationship chain. Deletion will be represented by tombstones rather than physically deleting the record, so that sync can reconcile offline deletes safely.

## Files changed

### Updated files

- [pom.xml](../pom.xml)
  - Java version aligned to 21.
  - Maven and Docker config now match the runtime environment.

- [application.yml](../src/main/resources/application.yml)
  - Removed the unsafe default admin credentials.
  - Kept Flyway validation-only mode.
  - Removed the explicit PostgreSQL dialect override and set `open-in-view` to false.
  - Required JWT secret validation remains in place.

- [application-dev.yml](../src/main/resources/application-dev.yml)
  - Flyway clean disabled for safety.

- [Dockerfile](../Dockerfile)
  - Switched build and runtime base images to Java 21-compatible images.

- [compose.yaml](../compose.yaml)
  - Fixed the database URL to respect `DB_NAME`.
  - Made JWT secret required before backend startup.

- [.env.example](../.env.example)
  - Emphasized that `JWT_SECRET` must be set to a private random secret value.

- [MigrationSqlContractTest.java](../src/test/java/com/fieldnotes/MigrationSqlContractTest.java)
  - Expanded the SQL contract checks to cover both V1 and V2.

- [verification-phase-report.md](../docs/verification-phase-report.md)
  - Rewritten as the official Phase A audit record.

### Added files

- [V2__add_sync_tombstones_and_restrict_deletes.sql](../src/main/resources/db/migration/V2__add_sync_tombstones_and_restrict_deletes.sql)
- [FieldNotesUserDetailsService.java](../src/main/java/com/fieldnotes/user/FieldNotesUserDetailsService.java)

### Removed files

- [FieldNotesApplicationTests.java](../src/test/java/com/fieldnotes/FieldNotesApplicationTests.java)
  - This was an overly trivial placeholder test and was removed in favor of meaningful migration and context checks.

## Security foundation fix

The app was failing during startup because `SecurityConfig` constructs an authentication manager that requires a `UserDetailsService`, but no concrete bean existed. I added the repository-backed `FieldNotesUserDetailsService` to bind Spring Security to the user store.

This is a foundation fix only; it does not yet implement registration, login, or JWT issuance endpoints. It resolves the context startup issue so the project can move forward in a valid state.

## Validation and test results

### Commands run

```powershell
Set-Location 'd:\Field_Notes_BackendDB\field-notes-backend'
& 'C:\Tools\maven\apache-maven-3.9.9\bin\mvn.cmd' -q clean test
```

### Result

- Exit code: 0
- Tests: 3 total
- Failures: 0
- Errors: 0
- Skipped: 0

This confirms the application context boots successfully in the test profile and the migration contract tests pass for the current foundation state.

## Remaining blockers and gaps

The following remain untested or not implemented because the required runtime/tooling or product behavior is not yet in place:

1. PostgreSQL runtime validation
   - Docker CLI is unavailable.
   - PostgreSQL client tools are unavailable.
   - Flyway could not be executed against a live PostgreSQL database in this environment.

2. Neon DB connectivity
   - No Neon JDBC connection string or host credentials were provided.

3. Authentication business flow
   - Registration, login, JWT issue/validation, and ownership-aware endpoints are not yet implemented.

4. CRUD and sync APIs
   - Customer, site, and field-note CRUD remains future work.
   - Offline synchronization logic and conflicting updates remain future work.

5. Photo storage
   - Metadata is present in the schema, but persistent object storage and upload handling are not implemented.

## Questions / declarations required before Phase B

The foundation is in a valid state, but a few implementation and deployment decisions still need your confirmation to avoid building the wrong thing.

1. API host / deployment target
   - Please confirm the final public API host for the Spring Boot service.
   - Example format: `https://<confirmed-api-host>/api/v1`
   - We should not hardcode a hostname without your final deployment choice.

2. Neon database access
   - Please provide the Neon PostgreSQL JDBC connection string and any environment credentials needed for the backend runtime.
   - Without this, live database validation cannot be completed.

3. Photo storage strategy
   - Please confirm where uploaded field-note photos will be stored for production.
   - Recommended minimal option: persistent object storage such as Azure Blob Storage, S3-compatible storage, or another provider you already use.
   - The backend should keep metadata in PostgreSQL and store file bytes outside the app container.

4. Final deployment provider
   - Please confirm whether the backend will be deployed to a cloud host, app platform, or container service, and whether you want to keep the deployment plan provider-agnostic for now.

5. Default note status policy
   - Confirm that the Default Note Status remains Flutter-local only and is never synchronized to the backend, as planned.

6. Auth scope
   - Confirm that ordinary authenticated users with strict ownership enforcement are sufficient and that no role-management system is needed at this stage.

These are not blocking for code structure, but they matter for the final deployment and API contract.

## Deployment info required later for the `atiltdfieldnotes...` host

The backend should be configured so the app uses a separate public API base URL, for example:

- `https://<confirmed-api-host>/api/v1`

This must remain distinct from the Neon JDBC connection string.

To activate this later, the project will need:

1. A hosting provider or runtime service for the Spring Boot application
2. A public hostname or custom domain under your control
3. DNS access and HTTPS/TLS configuration for that hostname
4. The Neon JDBC connection string and credentials as private backend secrets
5. The final public API base URL configured in Flutter as `API_BASE_URL`

No custom hostname is assumed or hardcoded in the code.

## Phase A gate

Phase A achieved its goal: backend foundation inspection, real correction of startup and configuration defects, schema preparation for sync-safe deletion and versioning, and successful local test validation under the available toolchain.

The project is not feature-complete, and deployment/production validation remains blocked until the environment and deployment details above are confirmed.

## Summary

This is a clean foundation audit, not a finished backend. The backend is now in a valid, testable starting state for Phase B: implementing authentication and user-isolated business APIs.

## Audit status

- Status: completed for Phase A
- Scope: foundation and schema validation only
- Build/test validation: passed using Maven in the current environment
- Remaining work: auth, CRUD, sync, and deployment configuration
- Known blockers: no Docker/PostgreSQL runtime available here, no Neon connection string, no confirmed API hostname
