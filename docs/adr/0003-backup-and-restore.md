# ADR 0003: Backups belong to the storage providers

## Status

Accepted for the first production design

## Decision

The application will not contain its own backup scheduler.

- PostgreSQL should use encrypted provider snapshots plus point-in-time recovery.
  The working targets are a 15-minute recovery point and a four-hour recovery
  time. These are design assumptions, not a current service guarantee.
- Object storage should enable versioning and keep a second encrypted copy in a
  separate failure boundary.
- Redis is disposable. Jobs originate in PostgreSQL's outbox and live vehicle
  positions can be rebuilt from retained telemetry.
- A restore is not considered proven until a clean environment starts, Flyway
  validates, PostGIS reports healthy, and a sample evidence package can be read.
  The team should rehearse this at least every three months before production.

## Why

Database and object-store providers already solve durable snapshots better than
application code can. The backend should make recovery testable and avoid
treating Redis as the only copy of business work.

## Consequences

Local development has no recovery guarantee. A production launch is blocked
until a named owner, provider policy, encryption key, alert, and successful
restore rehearsal exist for PostgreSQL and object storage.
