# Backend architecture and reasoning

## Recommended stack

- Java 21
- Spring Boot 3.5.16
- PostgreSQL 16
- Maven
- Flyway
- Spring Security + JWT
- Springdoc OpenAPI
- Docker Compose for local environment

Spring Boot 3.5.16 is the stable 3.5 release declared in Maven. Java 21 is the project's LTS baseline and matches the available local runtime and Docker images.

The public API base URL and Neon JDBC URL are separate configuration values with different consumers: Flutter uses one environment-specific `API_BASE_URL`; only the hosted backend receives the private JDBC URL and credentials.

## Why this architecture

This stack is a good fit for a mobile-first backend because it emphasizes predictable validation, secure authentication, type-safe APIs, and a mature relational data model. PostgreSQL gives strong referential integrity and search support needed for customer/site/note relationships. Spring Boot keeps the app modular and extensible while staying approachable for a small-to-medium API.

## Proposed package layout

```text
field-notes-backend/
├── src/
│   ├── main/
│   │   ├── java/com/fieldnotes/
│   │   │   ├── FieldNotesApplication.java
│   │   │   ├── auth/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── entity/
│   │   │   │   ├── repository/
│   │   │   │   ├── security/
│   │   │   │   └── service/
│   │   │   ├── common/
│   │   │   │   ├── exception/
│   │   │   │   ├── response/
│   │   │   │   └── validation/
│   │   │   ├── config/
│   │   │   ├── customer/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── entity/
│   │   │   │   ├── mapper/
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── site/
│   │   │   ├── fieldnote/
│   │   │   └── user/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   └── test/
│       └── java/com/fieldnotes/
├── docs/
├── .env.example
├── .gitignore
├── compose.yaml
├── Dockerfile
├── pom.xml
├── README.md
└── .mvn/wrapper/
```

## Layer responsibilities

### Controllers

Expose HTTP endpoints and translate API requests into service calls. They should not contain business logic.

### Services

Encapsulate authorization rules, validation, and persistence orchestration.

### Repositories

Use Spring Data JPA repository interfaces and custom query methods for persistence operations.

### Entities

Map directly to database tables while keeping persistence concerns isolated from API contracts.

### DTOs

Represent external request/response payloads and provide validation conversion points.

### Security components

Handle password hashing, JWT parsing, user retrieval, and access checks.

### Common infrastructure

Centralizes validation, global exception handling, API response models, and reusable utilities.

## Decision: feature-oriented not over-abstracted

The requested scope is not large enough to justify a heavy domain-driven design structure. A straightforward layered architecture with feature packages and a common security/error layer gives the best maintainability at minimal complexity.
