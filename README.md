# Field Notes Backend

This repository contains the Field Notes backend scaffold and its phased implementation. The foundation and schema migrations are prepared; authentication and business APIs are not yet complete.

## Included

- Spring Boot 3.5.16 + Java 21 Maven project scaffold
- Docker Compose setup for PostgreSQL and the backend service
- Flyway migration skeleton for the initial schema
- Architecture and planning documents under `docs/`
- Environment-variable template and project configuration
- Forward-only Flyway migration for sync tombstones and revision numbers

## Documentation index

- [docs/README.md](docs/README.md)
- [docs/requirements-analysis.md](docs/requirements-analysis.md)
- [docs/backend-architecture.md](docs/backend-architecture.md)
- [docs/database-schema.md](docs/database-schema.md)
- [docs/api-specification.md](docs/api-specification.md)
- [docs/authentication-authorization.md](docs/authentication-authorization.md)
- [docs/docker-architecture.md](docs/docker-architecture.md)
- [docs/synchronization-design.md](docs/synchronization-design.md)
- [docs/testing-strategy.md](docs/testing-strategy.md)
- [docs/implementation-roadmap.md](docs/implementation-roadmap.md)
- [docs/decisions-and-questions.md](docs/decisions-and-questions.md)
- [docs/phase-a-backend-foundation-audit.md](docs/phase-a-backend-foundation-audit.md)
- [docs/verification-phase-report.md](docs/verification-phase-report.md)
- [docs/phase-b-domain-implementation-report.md](docs/phase-b-domain-implementation-report.md)

## Local development

1. Copy `.env.example` to `.env` and adjust values.
   Set `JWT_SECRET` to a private random secret of at least 32 bytes before startup. Do not commit `.env`.
2. Start PostgreSQL and the backend with Docker Compose:
   ```bash
   docker compose up --build
   ```
3. Open the API docs at `http://localhost:8080/swagger-ui.html`.

## API address and hosted deployment

The Flutter app's API base URL is distinct from the backend's Neon JDBC URL. Configure the app with one environment-specific `API_BASE_URL`, for example `https://<confirmed-api-host>/api/v1`; no custom domain is assumed or hardcoded here.

To activate an `atiltdfieldnotes...` hostname later, deployment will require a confirmed hosting provider/service for the Spring Boot container, the provider's public backend hostname, a domain that you control (or the provider-generated hostname), DNS access to create the required records if using a custom domain, and HTTPS/TLS enablement. The backend deployment also needs the Neon JDBC URL and database credentials as private environment secrets. Flutter needs only the public HTTPS API base URL, never the database URL.
