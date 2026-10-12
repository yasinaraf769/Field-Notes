# Architectural decisions, assumptions, and unresolved questions

## Decisions made

- Single-user app model with user-owned records.
- PostgreSQL as relational store with UUID primary keys.
- Java 21 + Spring Boot 3.5.16 as the backend baseline.
- JWT access tokens plus refresh token rotation support.
- Flyway-first schema design.
- Docker Compose for PostgreSQL and local app workflow.
- Feature-oriented backend package structure.
- Client-generated UUIDs for customer, site, and note records; database defaults are fallbacks only.
- Soft-delete tombstones and optimistic revision columns for synchronization; physical parent deletion is restricted.
- Default Note Status remains in Flutter local storage and is not exposed by the backend.
- A public API URL is configured separately from the private Neon JDBC connection; no unverified custom domain is assumed.

## Deployment information needed later

Activation of the planned `atiltdfieldnotes...` API hostname will require:

1. The hosting provider/service selected for the Spring Boot container and its generated public hostname.
2. A custom domain under your control if you want a branded hostname rather than the generated provider hostname.
3. Access to manage that domain's DNS records, plus the provider's required record values and HTTPS/TLS setup.
4. The hosted Neon JDBC URL and database credentials, supplied only as backend deployment secrets.
5. The final public HTTPS API base URL to place in Flutter's environment-specific `API_BASE_URL`.

No hostname or domain ownership is assumed. The backend can be prepared before these deployment details are available.
