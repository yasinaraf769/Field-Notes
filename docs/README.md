# Backend Architecture and Planning

This folder holds the initial architecture and planning output for the Field Notes backend.

## Document map

- [requirements-analysis.md](requirements-analysis.md) — concise scope and business interpretation.
- [backend-architecture.md](backend-architecture.md) — layered architecture and package layout.
- [database-schema.md](database-schema.md) — PostgreSQL schema, constraints, indexes, and ER diagram.
- [api-specification.md](api-specification.md) — endpoint contracts and validation expectations.
- [authentication-authorization.md](authentication-authorization.md) — JWT and access-control design.
- [docker-architecture.md](docker-architecture.md) — container architecture and runtime decisions.
- [synchronization-design.md](synchronization-design.md) — offline-first synchronization strategy.
- [testing-strategy.md](testing-strategy.md) — validation plan for the backend and data model.
- [implementation-roadmap.md](implementation-roadmap.md) — phased implementation plan.
- [decisions-and-questions.md](decisions-and-questions.md) — major choices, assumptions, and blockers.
- [phase-a-backend-foundation-audit.md](phase-a-backend-foundation-audit.md) — Phase A foundation audit.
- [verification-phase-report.md](verification-phase-report.md) — Phase A verification report.
- [phase-b-domain-implementation-report.md](phase-b-domain-implementation-report.md) — Phase B security hardening & domain implementation report.
- [phase-c-offline-sync-implementation-report.md](phase-c-offline-sync-implementation-report.md) — Phase C offline synchronization & hardening report.

## Recommended baseline decisions

- Java 21
- Spring Boot 3.5.16
- PostgreSQL 16
- Flyway for schema versioning
- JWT access tokens with refresh-token support
- Feature-oriented package layout under `com.fieldnotes`
- Docker Compose for local development

## Scope gate

The backend foundation is scaffolded, but authentication, CRUD APIs, synchronization, and photo upload are not complete. Java 21 was selected to match the available LTS runtime and the Docker images. Hibernate schema generation remains validation-only; Flyway owns schema changes.
