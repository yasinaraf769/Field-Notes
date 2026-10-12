# Phase B: Security Hardening & Core Domain Implementation Report

## Summary

This report documents the completion of Phase B (Phases 3 through 6 on the roadmap) for the Field Notes backend:
1. Debugged and fixed security exception handling, JWT uniqueness, deactivated user checks, and CORS configuration.
2. Completed Phase 3 Core Security: Refresh token persistence in PostgreSQL (`refresh_tokens` table) with SHA-256 hashing, token rotation, and logout revocation.
3. Implemented Phase 4 Customer Domain: CRUD endpoints, client-generated UUID support, pagination, keyword search, strict ownership isolation, revision counter (`version`), and soft-delete tombstones (`deleted_at`).
4. Implemented Phase 5 Site Domain: Parent-child customer foreign key validation, anti-IDOR checks, address & geolocation metadata, pagination, search, revisions, and soft-delete tombstones.
5. Implemented Phase 6 Field Note Domain: Multi-criteria filtering by site, customer, and status (`PENDING`, `IN_PROGRESS`, `COMPLETED`), title/description search, photo reference support, hierarchical ownership checks, revisions, and soft-delete tombstones.
6. Expanded integration test suite to **30 comprehensive tests** across all domains, passing with 0 failures and 0 errors.

---

## Bugs Identified & Resolved

### 1. Spring Security Authentication Exception handling (HTTP 500 -> HTTP 401)
- **Problem**: When a user provided invalid credentials or an unknown email during login, Spring Security's `AuthenticationManager` threw `BadCredentialsException`/`AuthenticationException`. Because `GlobalExceptionHandler` lacked a handler for `AuthenticationException`, it fell through to generic `Exception.class`, returning HTTP 500 Internal Server Error instead of 401 Unauthorized.
- **Fix**: Added `@ExceptionHandler(AuthenticationException.class)` in [GlobalExceptionHandler.java](../src/main/java/com/fieldnotes/common/exception/GlobalExceptionHandler.java) returning HTTP 401 with standard `ApiError`.

### 2. Spring Security Access Denied handling (HTTP 500 -> HTTP 403)
- **Problem**: `AccessDeniedException` fell through to generic exception handling, returning HTTP 500 instead of 403 Forbidden.
- **Fix**: Added `@ExceptionHandler(AccessDeniedException.class)` returning HTTP 403 Forbidden.

### 3. Malformed JSON handling (HTTP 500 -> HTTP 400)
- **Problem**: `HttpMessageNotReadableException` on malformed request bodies returned HTTP 500.
- **Fix**: Added handler returning HTTP 400 Bad Request with "Malformed JSON request".

### 4. Database constraint violation handling (HTTP 500 -> HTTP 409)
- **Problem**: Concurrent duplicate registrations or database constraint violations threw `DataIntegrityViolationException`, resulting in HTTP 500.
- **Fix**: Added handler returning HTTP 409 Conflict.

### 5. Standardized entry point and access denied error responses
- **Problem**: Unauthenticated requests to protected endpoints returned standard Spring white-label or empty responses instead of unified `ApiError` JSON format.
- **Fix**: Configured custom `AuthenticationEntryPoint` and `AccessDeniedHandler` in [SecurityConfig.java](../src/main/java/com/fieldnotes/config/SecurityConfig.java).

### 6. Dynamic CORS configuration
- **Problem**: CORS allowed origins were hardcoded in `SecurityConfig.java` rather than reading from `app.cors.allowed-origins`.
- **Fix**: Injected allowed origins from application properties.

### 7. Deactivated user token invalidation
- **Problem**: If a user was marked inactive, existing valid JWTs were still accepted by `JwtAuthenticationFilter`.
- **Fix**: Added `user.isActive()` check in [JwtAuthenticationFilter.java](../src/main/java/com/fieldnotes/auth/security/JwtAuthenticationFilter.java).

### 8. Refresh token collision within the same second
- **Problem**: When JWT tokens were generated in quick succession (same second), claims (`sub`, `email`, `iat`, `exp`) were identical, resulting in identical token strings.
- **Fix**: Added unique JWT ID (`jti`) via `UUID.randomUUID()` in [JwtService.java](../src/main/java/com/fieldnotes/auth/security/JwtService.java) for every access and refresh token.

---

## Domain Implementations

### Core Security (Phase 3)
- **Entities & Tables**: Mapped `RefreshToken` to `refresh_tokens` table.
- **Storage & Security**: Refresh tokens are stored with SHA-256 hashed values; tokens are revoked upon use (rotation) and upon logout.
- **Endpoints**:
  - `POST /api/v1/auth/register` (201 Created)
  - `POST /api/v1/auth/login` (200 OK)
  - `POST /api/v1/auth/refresh` (200 OK with token rotation)
  - `POST /api/v1/auth/logout` (204 No Content)

### Customer Domain (Phase 4)
- **Entity**: [Customer.java](../src/main/java/com/fieldnotes/customer/entity/Customer.java) with `user_id`, client UUID fallback, `deleted_at` tombstone, and `version` revision counter.
- **Repository**: [CustomerRepository.java](../src/main/java/com/fieldnotes/customer/repository/CustomerRepository.java) with user isolation, active filters, and search query.
- **Service & Controller**: [CustomerService.java](../src/main/java/com/fieldnotes/customer/service/CustomerService.java) and [CustomerController.java](../src/main/java/com/fieldnotes/customer/controller/CustomerController.java).
- **Endpoints**:
  - `GET /api/v1/customers` (paginated, sorted, searchable)
  - `GET /api/v1/customers/{id}` (ownership validated)
  - `POST /api/v1/customers` (accepts client-generated UUID or generates server UUID)
  - `PUT /api/v1/customers/{id}` (full update, advances version)
  - `PATCH /api/v1/customers/{id}` (partial update, advances version)
  - `DELETE /api/v1/customers/{id}` (soft-delete tombstone, advances version)

### Site Domain (Phase 5)
- **Entity**: [Site.java](../src/main/java/com/fieldnotes/site/entity/Site.java) with `customer_id`, address fields, geographic coordinates, `deleted_at`, and `version`.
- **Repository**: [SiteRepository.java](../src/main/java/com/fieldnotes/site/repository/SiteRepository.java).
- **Service & Controller**: [SiteService.java](../src/main/java/com/fieldnotes/site/service/SiteService.java) and [SiteController.java](../src/main/java/com/fieldnotes/site/controller/SiteController.java).
- **Endpoints**:
  - `GET /api/v1/sites` (paginated, searchable, customerId filter)
  - `GET /api/v1/sites/{id}`
  - `POST /api/v1/sites` (verifies parent customer ownership)
  - `PUT /api/v1/sites/{id}`
  - `PATCH /api/v1/sites/{id}`
  - `DELETE /api/v1/sites/{id}`

### Field Note Domain (Phase 6)
- **Entity**: [FieldNote.java](../src/main/java/com/fieldnotes/fieldnote/entity/FieldNote.java) with `site_id`, `NoteStatus` enum (`PENDING`, `IN_PROGRESS`, `COMPLETED`), `note_date`, photo reference, geo metadata, `deleted_at`, and `version`.
- **Repository**: [FieldNoteRepository.java](../src/main/java/com/fieldnotes/fieldnote/repository/FieldNoteRepository.java).
- **Service & Controller**: [FieldNoteService.java](../src/main/java/com/fieldnotes/fieldnote/service/FieldNoteService.java) and [FieldNoteController.java](../src/main/java/com/fieldnotes/fieldnote/controller/FieldNoteController.java).
- **Endpoints**:
  - `GET /api/v1/field-notes` (filtered by siteId, customerId, status, and keyword search)
  - `GET /api/v1/field-notes/{id}`
  - `POST /api/v1/field-notes` (hierarchical ownership verification: note -> site -> customer -> user)
  - `PUT /api/v1/field-notes/{id}`
  - `PATCH /api/v1/field-notes/{id}`
  - `DELETE /api/v1/field-notes/{id}`

---

## Test Verification Summary

Command executed:
```powershell
& 'C:\Tools\maven\apache-maven-3.9.9\bin\mvn.cmd' test
```

Results:
- **ApplicationConfigurationTest**: 1 test passed
- **MigrationSqlContractTest**: 2 tests passed
- **AuthControllerTest**: 9 tests passed
- **CustomerControllerTest**: 6 tests passed
- **SiteControllerTest**: 6 tests passed
- **FieldNoteControllerTest**: 6 tests passed
- **Total**: **30 tests run, 0 failures, 0 errors, 0 skipped**.
- **Exit code**: 0 (BUILD SUCCESS).
