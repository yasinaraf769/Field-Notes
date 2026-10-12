# Testing strategy

## Test layers

### Unit tests

- DTO validation
- controller request mapping
- service ownership checks
- password hashing and token creation logic
- mapper conversions

### Integration tests

- registration/login
- JWT-protected access
- customer CRUD flows
- site CRUD and parent-child validation
- field note CRUD and filtering
- unauthorized cross-user access attempts
- Flyway migration verification

### Docker-based tests

- run the app against the `docker compose` PostgreSQL service
- verify that environment variables are applied correctly
- ensure health checks and startup dependencies behave as expected

## Recommended test examples

- `AuthControllerIT` for register/login/logout flow
- `CustomerControllerIT` for list/create/get/update/delete
- `SiteControllerIT` for parent customer enforcement
- `FieldNoteControllerIT` for status filtering and site linkage
- `UserIsolationIT` for verifying user A cannot access user B resources
- `ValidationIT` for malformed payload failures

## Migration validation

Verify each Flyway migration specifically by running the application against a fresh PostgreSQL instance and validating that schema objects exist with correct constraints.

## Future synchronization tests

- duplicate retry handling
- client-generated ID idempotence
- conflict detection when same record changed on server and client
- delete tombstone behavior

## Test tooling

- JUnit 5
- Spring Boot Test
- MockMvc for HTTP-level tests
- Testcontainers for database-backed integration tests if needed in a later phase
