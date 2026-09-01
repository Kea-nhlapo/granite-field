# ADR 0002: PostGIS is provisioned, not migrated

## Status

Accepted

## Decision

Creating the PostGIS extension is a provisioning responsibility. The Flyway
migration `V20260901203000__database_require_postgis.sql` REQUIRES the extension
and fails with a message naming the fix; it does not create it.

Locally the `postgis/postgis` image installs the extension at initdb, in both
`infra/containers/docker-compose.yml` and the Testcontainers base class. Every
non-local environment must run `CREATE EXTENSION postgis` as a superuser before
the application is first deployed.

## Why

`CREATE EXTENSION` requires superuser or a provider-specific elevated role. On
RDS, Cloud SQL, and Azure Flexible Server the application's Flyway role does not
have it, so the original `CREATE EXTENSION IF NOT EXISTS postgis` migration
would have failed on the first real deploy. It passed only because local and
test databases both use the postgis image, which had already done the work — the
migration had never once had to do its job.

**Rejected — delete the migration and leave it entirely to provisioning.** This
removes the deploy-time guarantee. A fresh environment missing PostGIS would
start clean and fail later, inside an unrelated spatial migration or at runtime
in a routing query, at a point far from the actual cause.

**Rejected — grant the Flyway role `rds_superuser` or equivalent.** This is what
most teams do, and it solves a one-time bootstrap problem by permanently giving
every future schema migration the right to drop any object in the database. It
is also not portable: each provider spells the role differently.

**Constraint that decided it:** the migration role must not be a superuser, but
a missing extension must still surface at deploy time rather than at 2am in a
routing query. Requiring rather than creating satisfies both.

## Consequences

- Provisioning for every non-local environment must create the extension before
  the first deploy. `PostgisRequirementTest` asserts the failure message that a
  team who forgets will see.
- **The check is one-shot.** A versioned migration runs exactly once per
  database. If PostGIS is later dropped, or a restored snapshot lacks it, Flyway
  sees version 20260901203000 already applied and skips. A standing guarantee
  would be an actuator health indicator, which is separate work and not in
  issue #2.
- Renaming `..._enable_postgis` to `..._require_postgis` changes both the Flyway
  checksum AND the description stored in `flyway_schema_history`; validation
  fails on either. Developers with an existing local volume must run
  `docker compose down -v` once. No environment is deployed, so nothing else is
  affected.
