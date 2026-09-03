# Work split

Documented from GitHub issue #27. One issue, one short-lived branch, one pull
request. Rebase on `main` before review.

## Backend Lane A: application and commerce

1. #1 Spring Boot bootstrap
2. #3 authentication and tenant access
3. #7 business onboarding
4. #8 supplier invitations
5. #9 documents
6. #10 procurement
7. #11 comparison rules
8. #12 aggregation
9. #13 notifications

## Backend Lane B: data and logistics

1. #2 PostgreSQL, PostGIS, and Flyway
2. #4 events and background jobs
3. #14 transport capacity
4. #16 route provider
5. #15 capacity matching after #12 is ready
6. #17 route scoring
7. #18 shipments
8. #19 telemetry
9. #20 risk
10. #21 handovers

## Shared integration

- #5 storage can be taken by whichever lane reaches document upload first.
- #6 CI should begin as soon as #1 and #2 merge.
- #22 evidence, #23 trust, #24 insurance, #25 integration, and #26 hardening
  follow their listed dependencies.

## Frontend lane

1. #28 frontend bootstrap
2. #29 shared API contract, generated client, and mocks
3. #30 session shell and route guards
4. #31 through #39 by feature, following listed backend dependencies
5. #40 frontend quality coverage as journeys land

## Monorepo ownership

- Backend work stays under `apps/backend`.
- Frontend work stays under `apps/frontend`.
- Contract work stays under `packages/api-contracts`.
- A pull request should not change both applications unless it is deliberately
  integrating an agreed contract.
