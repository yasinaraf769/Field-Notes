# Deploying Field Notes Backend with Neon PostgreSQL

> **Status note**: All commands in this document describe the **intended** procedure.  
> They have **not been run on this machine** because Docker is not installed.  
> Each section is labelled **VERIFIED** or **UNVERIFIED** accordingly.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker | ≥ 24 | Required to build and run the container |
| Docker Compose | ≥ 2.20 | Only for local integration testing |
| Java 21 (Temurin) | 21.x | Only needed to run tests without Docker |
| Maven | 3.9.x | Bundled inside Docker build stage |
| Neon account | — | https://neon.tech — free tier sufficient |

---

## 2. Neon Database Setup

### 2.1 Create a Neon project

1. Log in to [console.neon.tech](https://console.neon.tech).
2. Create a new project (e.g., `field-notes`).
3. Neon creates a default database and role. Note the connection strings:
   - **Direct endpoint** (non-pooler): `<project>.neon.tech`
   - **Pooler endpoint**: `<project>.pooler.neon.tech`

### 2.2 Which endpoint to use

| Purpose | Endpoint to use | Reason |
|---|---|---|
| Application (Hikari pool) | Pooler | Neon's built-in PgBouncer; handles connection multiplexing |
| Flyway migrations | **Direct** | The pooler does not support the `SET` statements Flyway uses |

### 2.3 JDBC URL format

```
# Application / Hikari (use pooler):
jdbc:postgresql://<project>.pooler.neon.tech/<database>?sslmode=require

# Flyway migrations (use direct endpoint):
jdbc:postgresql://<project>.neon.tech/<database>?sslmode=require
```

If you run migrations and the application with a **single** `DB_URL`, use the **direct** endpoint. You can configure a separate `FLYWAY_URL` env var if you want pooler for the app and direct for migrations (see Section 4.2).

---

## 3. Environment Variables

All secrets are injected at runtime. No secrets have default values in `application.yml`.  
Copy `.env.example` to `.env` and populate every field.

| Variable | Required | Description |
|---|---|---|
| `DB_URL` | **Yes** | JDBC URL to the Neon database (see Section 2.3) |
| `DB_USERNAME` | **Yes** | Neon database username |
| `DB_PASSWORD` | **Yes** | Neon database password |
| `JWT_SECRET` | **Yes** | At least 32 characters. Generate: `openssl rand -hex 32` |
| `JWT_EXPIRATION_MS` | No | Token lifetime in milliseconds. Default: `3600000` (1 hour) |
| `PORT` | No | HTTP port the server listens on. Default: `8080`. Render/Railway/Fly.io inject this automatically. |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed origins. Default: `http://localhost:3000,http://localhost:8081` |
| `JAVA_OPTS` | No | JVM flags injected at container startup. Example: `-XX:+UseContainerSupport -Xmx256m` |

### Example `.env` for a Neon deployment

```dotenv
DB_URL=jdbc:postgresql://ep-cool-project-123456.neon.tech/field_notes?sslmode=require
DB_USERNAME=field_notes_owner
DB_PASSWORD=<generated-by-neon>
JWT_SECRET=<output-of-openssl-rand-hex-32>
JWT_EXPIRATION_MS=3600000
PORT=8080
CORS_ALLOWED_ORIGINS=https://your-flutter-app.example.com
JAVA_OPTS=-XX:+UseContainerSupport -Xmx256m
```

---

## 4. Running the Application

### 4.1 Build the Docker image

**UNVERIFIED** — Docker is not installed on the development machine where this was written.

```bash
docker build -t field-notes-backend:latest .
```

The multi-stage build:
1. Stage 1 (`maven:3.9.9-eclipse-temurin-21`): downloads dependencies, runs `mvn package -DskipTests`.
2. Stage 2 (`eclipse-temurin:21-jre-jammy`): copies the fat JAR, creates a non-root `appuser`, and sets `HEALTHCHECK`.

### 4.2 Run with Docker (Neon as database)

**UNVERIFIED**

```bash
docker run --rm \
  --env-file .env \
  -p 8080:8080 \
  field-notes-backend:latest
```

Or, if you want to override JAVA_OPTS inline:

```bash
docker run --rm \
  --env-file .env \
  -e JAVA_OPTS="-XX:+UseContainerSupport -Xmx256m" \
  -p 8080:8080 \
  field-notes-backend:latest
```

### 4.3 Flyway migrations

Flyway runs automatically on startup via Spring Boot auto-configuration.  
Migrations are located in `src/main/resources/db/migration/`:

| File | Description |
|---|---|
| `V1__initial_schema.sql` | Core tables: users, refresh tokens, customers, sites, field_notes |
| `V2__add_refresh_tokens.sql` | Refresh token support |
| `V3__add_device_sync_cursors.sql` | Device sync cursor table |

If Flyway fails due to the pooler endpoint, set a dedicated `FLYWAY_URL` pointing to the direct endpoint:

```yaml
# In application.yml (add if needed):
spring:
  flyway:
    url: ${FLYWAY_URL:${DB_URL}}
    user: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### 4.4 Deploying to Render

**UNVERIFIED**

1. Push this repository to GitHub.
2. In the Render dashboard, create a **Web Service** and connect the repo.
3. Set **Runtime** to `Docker`.
4. Set all environment variables from Section 3 in the Render dashboard (do not use a `.env` file on Render; use their secrets manager).
5. Render injects `PORT` automatically — do not hardcode it.
6. The health check URL is `/actuator/health`.

### 4.5 Running locally with Docker Compose (local Postgres)

**UNVERIFIED**

```bash
# Ensure .env exists with at minimum JWT_SECRET set:
cp .env.example .env
# Edit .env and set JWT_SECRET to a real value.

docker compose up --build
```

`compose.yaml` starts a local `postgres:16-alpine` container and the backend. The backend's health check polls `/actuator/health`.

> **Note**: When using `compose.yaml`, `DB_URL` is set to the internal Docker network address (`jdbc:postgresql://postgres:5432/field_notes`). This is correct for local Docker Compose runs only. Do **not** use this URL for a Neon-backed deployment.

---

## 5. Hikari Pool Settings (Neon Auto-Suspend)

Neon automatically suspends the database after ~5 minutes of inactivity. The following Hikari settings in `application.yml` are tuned for this:

| Setting | Value | Reason |
|---|---|---|
| `minimum-idle` | `0` | Pool drains completely when the DB suspends; avoids keeping dead connections |
| `max-lifetime` | `300000` ms | Recycles connections every 5 min, before Neon forcibly closes them |
| `connection-timeout` | `30000` ms | Gives Neon up to 30 s to wake from auto-suspend |
| `maximum-pool-size` | `5` | Conservative; Neon free tier has a connection limit |

---

## 6. Security Configuration — Current State and Recommendations

### 6.1 What is currently allowed

| Setting | Current value | Location |
|---|---|---|
| CORS origins | `${CORS_ALLOWED_ORIGINS}` env var; defaults to `localhost:3000, localhost:8081` | `application.yml` + `SecurityConfig` |
| CORS methods | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | `SecurityConfig.corsConfigurationSource()` |
| CORS headers | `*` (all headers allowed) | `SecurityConfig.corsConfigurationSource()` |
| CORS credentials | `allowCredentials = true` | `SecurityConfig.corsConfigurationSource()` |
| Public endpoints | `/api/fieldnotesatl/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health` | `SecurityConfig.securityFilterChain()` |
| Session | Stateless (no server-side session) | `SecurityConfig.securityFilterChain()` |
| CSRF | Disabled (stateless JWT API) | `SecurityConfig.securityFilterChain()` |
| Actuator endpoints exposed | `health` only | `application.yml` management config |
| Actuator health details | `never` (no internals leaked) | `application.yml` management config |

### 6.2 Recommended tightening (not yet applied)

> These are **recommendations only**. No behavior has been changed without telling you.

1. **Tighten `allowed-headers`**: Change `"*"` to an explicit list:
   ```
   Authorization, Content-Type, Accept, X-Requested-With
   ```
   Wildcard headers with `allowCredentials=true` can be problematic in some browsers and are wider than needed.

2. **Remove Swagger/OpenAPI in production**: The `/v3/api-docs/**` and `/swagger-ui/**` permit rules expose your full API schema publicly. Consider:
   - Setting `springdoc.api-docs.enabled=false` in a `prod` profile, or
   - Protecting them with a separate admin role.

3. **Set `CORS_ALLOWED_ORIGINS` strictly in production**: The default includes `localhost` origins, which are safe only locally. In production, set this env var to your exact Flutter gateway domain.

4. **Add rate limiting** on `/api/fieldnotesatl/auth/**`: Registration and login endpoints have no rate limiting. A bucket4j or similar filter is recommended to prevent brute-force.

5. **Add `Strict-Transport-Security` header**: If you terminate TLS at a proxy (Render, Nginx), this is automatic. If not, add a security header filter.

None of the above have been applied. Let me know which ones to implement.

---

## 7. Health Check

The `/actuator/health` endpoint is:
- **Permitted without authentication** (configured in `SecurityConfig`).
- **Exposed** via `management.endpoints.web.exposure.include: health`.
- **Details hidden** (`show-details: never`) — returns only `{"status":"UP"}`.

```bash
# Smoke test: verify the server is up
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

---

## 8. Smoke-Test Checklist

Run these against a freshly deployed instance. Replace `BASE` with your actual URL.

```bash
BASE=http://localhost:8080/api/fieldnotesatl
```

### Step 1 — Health check

```bash
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Step 2 — Register a new user

```bash
curl -s -X POST $BASE/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"smoketest","password":"SmokeTest1!","email":"smoke@example.com"}' \
  | jq .
# Expected: 201 Created, body contains accessToken and refreshToken
```

### Step 3 — Login

```bash
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"smoketest","password":"SmokeTest1!"}' \
  | jq -r .accessToken)
echo "Token: $TOKEN"
# Expected: non-null JWT string
```

### Step 4 — Sync push (new customer)

```bash
CUSTOMER_ID=$(uuidgen)
curl -s -X POST $BASE/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"deviceId\": \"smoke-device-001\",
    \"lastSyncAt\": null,
    \"changes\": {
      \"customers\": [{
        \"id\": \"$CUSTOMER_ID\",
        \"name\": \"Smoke Customer\",
        \"phone\": \"555-0100\",
        \"email\": \"sc@example.com\",
        \"address\": \"1 Smoke St\",
        \"version\": 0,
        \"deletedAt\": null
      }],
      \"sites\": [],
      \"fieldNotes\": []
    }
  }" | jq .
# Expected: 200, conflicts=[], serverCursor is an ISO-8601 timestamp
```

### Step 5 — Sync pull (verify customer comes back)

```bash
curl -s -X POST $BASE/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"deviceId\": \"smoke-device-002\",
    \"lastSyncAt\": null,
    \"changes\": { \"customers\": [], \"sites\": [], \"fieldNotes\": [] }
  }" | jq .
# Expected: 200, delta.customers contains the pushed customer
```

### Step 6 — Repeated push (idempotency)

Push the exact same customer payload again (same id, same version 0, same content):

```bash
curl -s -X POST $BASE/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"deviceId\": \"smoke-device-001\",
    \"lastSyncAt\": null,
    \"changes\": {
      \"customers\": [{
        \"id\": \"$CUSTOMER_ID\",
        \"name\": \"Smoke Customer\",
        \"phone\": \"555-0100\",
        \"email\": \"sc@example.com\",
        \"address\": \"1 Smoke St\",
        \"version\": 0,
        \"deletedAt\": null
      }],
      \"sites\": [],
      \"fieldNotes\": []
    }
  }" | jq .
# Expected: 200, conflicts=[] (idempotent — no duplicate, no conflict)
```

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| App refuses to start — `JWT_SECRET not set` | Missing env var | Set `JWT_SECRET` to >= 32 chars |
| App refuses to start — `DB_URL not set` | Missing env var | Set `DB_URL` with correct Neon JDBC URL |
| `Connection timeout` on first request | Neon auto-suspend waking up | Normal; Hikari `connection-timeout=30000` gives 30 s |
| Flyway error: `cannot execute SET in a read-only transaction` | Using pooler endpoint for Flyway | Use the **direct** (non-pooler) endpoint for `DB_URL` |
| `{"status":"DOWN"}` from health check | DB unreachable or credentials wrong | Check Neon console; verify `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `401 Unauthorized` on sync | JWT expired or wrong `Authorization` header | Re-login and use the new `accessToken` |
| CORS error in browser | `CORS_ALLOWED_ORIGINS` does not include your origin | Add the exact origin (no trailing slash) to `CORS_ALLOWED_ORIGINS` |

---

*Document generated as part of Phase D deployment preparation. All Docker/Neon steps are UNVERIFIED on this machine.*
