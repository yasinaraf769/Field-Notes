# Docker architecture

## Recommended workflow

The project should run PostgreSQL in Docker for development while the Spring Boot app can run either locally or inside the same Docker network. This project recommends running the backend locally for faster iteration and PostgreSQL in Docker for a reliable database environment.

### Why this is preferred

- Faster local Java development and debugging
- Easier dependency management for Maven and IDE tooling
- Better compatibility with Spring Boot devtools and local test runs
- Still gives a stable database environment through Compose

## Compose services

### PostgreSQL

- Image: `postgres:16-alpine`
- Persistent volume: `postgres_data`
- Environment-driven credentials
- Healthcheck ensures readiness before backend startup

### Backend

- Build from local Dockerfile
- Environment-driven DB and JWT values
- Exposes 8080 to the host
- Depends on healthy PostgreSQL service before launch

## Container networking

The backend connects to PostgreSQL using the Compose service hostname `postgres` rather than `localhost` from inside the container network. This avoids host-binding jitter and supports a stable dev environment.

## Configuration plan

- `.env.example` contains placeholders only.
- Real values are never committed.
- Docker Compose reads from a local `.env` file when present.

## Start and stop commands

```bash
docker compose up --build
docker compose down
docker compose down -v
```

## Inspection commands

```bash
docker compose ps
docker compose logs -f postgres
docker compose logs -f backend
docker exec -it field-notes-postgres psql -U fieldnotes -d field_notes
```

## Alternative pattern

Running the entire backend in Docker is viable for production-like verification, but it tends to slow down day-to-day API work. The local-backend + Dockerized-database pattern is the better project default for this app.
