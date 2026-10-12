# Phase A: Backend foundation audit

## Inspected

Reviewed `pom.xml`, all Java source and test files, all Flyway migration files, `application.yml`, `application-dev.yml`, Dockerfile, Compose, `.env.example`, `.gitignore`, README, and architecture/schema/security/sync/testing/roadmap documentation.

## Corrections made

- Aligned the project to Java 21 and Spring Boot 3.5.16. Java 21 is installed locally; Boot 3.5.16 resolves and runs on it.
- Added V2 as a forward-only migration. V1 was not edited. V2 adds `deleted_at` and `version` columns to customers, sites, and field notes; replaces cascading resource foreign keys with `RESTRICT`; and adds update lookup indexes.
- Kept UUID database defaults as fallbacks. The schema can preserve a client-supplied UUID; duplicate/retry idempotency still requires API behavior and is not implemented.
- Made Flyway clean disabled in the dev profile and kept Hibernate DDL at validation-only in the default config.
- Removed the unsafe default JWT secret and in-memory admin credentials. JWT secret configuration is required and validated for at least 32 characters; expiration must be positive.
- Fixed Compose's database URL to honor `DB_NAME` and made a JWT secret mandatory before starting the backend.
- Added the missing repository-backed `UserDetailsService` required for the existing Spring Security authentication-manager wiring. Registration/login endpoints are not implemented.
- Corrected the schema and sync documentation. The meaningless always-true test was removed, and migration contract checks now cover both migration files.

## Versions

- Java: 21
- Spring Boot: 3.5.16
- Maven: 3.9.9
- PostgreSQL container image: 16-alpine

## Commands and results

Command executed:

```powershell
Set-Location 'd:\Field_Notes_BackendDB\field-notes-backend'
& 'C:\Tools\maven\apache-maven-3.9.9\bin\mvn.cmd' -q clean test
```

Result: **PASS**, exit code 0. This performed a clean Maven compilation and ran the test suite, including the H2 Spring context smoke test and Flyway SQL contract tests. Maven reports Spring Boot 3.5.16 and Java 21 at runtime.

The first `mvn test` attempt failed during Spring context startup because the configured authentication manager had no `UserDetailsService` bean. The missing repository-backed service was added; the final clean test run passed.

## Not tested / blocked

- PostgreSQL/Flyway execution against a PostgreSQL server: **not tested**; Docker and `psql` are unavailable. SQL contract tests inspect migration text and do not prove PostgreSQL executes the migration.
- Docker Compose build/startup: **blocked**, Docker CLI unavailable.
- Neon connection: **not tested**, no Neon JDBC URL/credentials supplied.
- Authentication endpoints, CRUD, user isolation, retry behavior, tombstones at runtime, and full sync: **not implemented or tested**.
- Version changes currently add a database revision column; incrementing it on updates and conflict handling must be implemented with CRUD/sync.
- Photo object storage and upload: **not implemented**.

## Deployment information needed for the `atiltdfieldnotes...` API hostname

No domain or public URL is assumed. Later activation requires:

1. Hosting provider/service for the Spring Boot container and its generated public hostname.
2. A domain under your control if using a custom hostname, plus DNS management access and provider-required DNS records.
3. HTTPS/TLS provisioning for the chosen hostname.
4. Neon JDBC URL and database credentials configured privately as backend deployment secrets (use the provider's recommended SSL-enabled connection string).
5. The final public HTTPS API base URL, configured once in Flutter as an environment-specific `API_BASE_URL`, e.g. `https://<confirmed-api-host>/api/v1`. Flutter must not receive the Neon JDBC URL.

## Phase A gate

**Foundation corrections and local Maven tests: complete. PostgreSQL migration execution and container validation: blocked by missing local tooling.** Proceeding to Phase B requires implementing authentication endpoints and tests; Phase A does not claim the backend is feature-complete.
