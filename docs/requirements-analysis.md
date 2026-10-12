# Requirements analysis

## Product understanding

The Field Notes application supports field workers who manage customer relationships, geographic sites, and visit notes. The backend must support user isolation, structured data modeling, and eventually offline-first synchronization for mobile usage.

## Core business needs

1. Authentication and account management for individual field workers.
2. Secured ownership boundaries: each user can work only with their own customers, sites, and notes.
3. Structured customer management with a rich contact model.
4. Site management tied to a parent customer with a one-to-many relationship.
5. Visit note management tied to a parent site, with status and optional geolocation metadata.
6. Search/filter endpoints to support field visibility and mobile workflows.
7. Support for future offline data creation, edits, conflict detection, and synchronization.

## Non-functional drivers

- Security by default.
- Traceable database integrity using foreign keys and constraints.
- Explicit migration management using Flyway.
- Clear API contracts for Flutter integration.
- Predictable local development environment with Docker and PostgreSQL.

## Assumptions to confirm

- The user model is a single app user, not multi-tenant organization model.
- Field workers are the only authenticated actors.
- Customers, sites, and notes are always user-owned data sets.
- Notes may be created offline and synced later using stable client-generated UUIDs.

## Out of scope for phase 1

- Full offline sync engine.
- File-upload storage for photos.
- Complex role model beyond authenticated user ownership.
- Advanced analytics, reporting, or admin dashboards.
