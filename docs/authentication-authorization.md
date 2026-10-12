# Authentication and authorization design

## Recommended design

Use a JWT-based stateless auth model with refresh tokens stored in PostgreSQL for revocation and rotation support.

### Flow

1. User registers or logs in.
2. Backend validates credentials against BCrypt-hashed password.
3. Backend issues short-lived access tokens and refresh tokens.
4. API requests include Bearer token in the `Authorization` header.
5. The JWT filter validates and loads the current user.
6. Ownership checks happen against the authenticated principal, not against a client-supplied `userId`.

## Password hashing

Use `BCryptPasswordEncoder` or a modern equivalent approved by Spring Security. BCrypt is robust and widely supported.

## JWT design

- Access token lifetime: 60 minutes by default
- Refresh token lifetime: 7 to 30 days depending on security policy
- Token claims: sub, email, roles, iat, exp
- Secret kept in environment variables, never committed

## Authorization rules

- Each user can only read, modify, and delete their own customers.
- Sites are only accessible when their parent customer is owned by the authenticated user.
- Field notes are only accessible when their parent site belongs to the same user.
- Ownership checks must be enforced in service methods, not only on controller endpoints.

## Security components

- `AuthenticationController` for registration/login/refresh/logout
- `JwtAuthenticationFilter` to validate tokens
- `JwtService` for token creation and parsing
- `UserDetailsService` for user lookup
- `SecurityConfig` for HTTP security and CORS
- `AccessDeniedHandler` and `AuthenticationEntryPoint` for consistent errors

## Preventing IDOR issues

This is a critical requirement. The backend must never trust a client-provided user ID to determine access. Instead:

- load the current user from the security context
- fetch the target resource by ID
- compare resource ownership to the authenticated user
- reject access with 403 or 404 as appropriate

## CORS

Allow localhost origins for the Flutter frontend in development and keep the configuration environment-driven. Production should be limited to approved origins.

## Recommended additional hardening

- Validate all request DTOs with Bean Validation
- Log security events without leaking secrets
- Keep exception messages generic for unauthorized or validation failures
- Avoid returning stack traces to clients
- Separate dev and prod profiles rigorously
