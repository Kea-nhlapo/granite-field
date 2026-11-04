# TradeMesh

TradeMesh helps small businesses source stock, share freight and get paid safely. A buyer can find verified suppliers, group orders with other buyers, book spare space on a truck that is already heading their way, track the load live, and only release payment once the delivery is scanned and checked.

We built it as a team for a hackathon. The full journey runs end to end, and the backend is deployed on AWS.

## What it does

- **Sourcing.** Post a product request, invite suppliers and compare their documents. Mismatched quotes and invoices get flagged automatically.
- **Demand grouping.** Similar requests from nearby buyers are grouped so they can share one delivery.
- **Spare capacity matching.** Loads are matched to trucks with free space, and backhaul trips get ranked so drivers don't drive home empty.
- **Route planning.** Candidate routes are scored with the cargo in mind, using PostGIS for the spatial work.
- **Live tracking.** Position updates come in during the trip. Route deviations and sudden fuel drops raise risk alerts.
- **Secure handover.** The receiver scans a signed QR code at delivery. If the quantity is wrong, payment stays locked until the dispute is resolved.
- **Escrow payments.** Money is held via Mobile Money (MoMo) and released only after a clean handover.
- **Trust and insurance.** Every trip feeds an append-only evidence ledger, a public trust score and a premium estimate that an insurer can review.
- **Notifications.** Consent-aware SMS and WhatsApp updates go out through Infobip, with signed delivery callbacks.

## Tech stack

| Layer | Tools |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring Security (JWT), Flyway, JDBC |
| Database | PostgreSQL with PostGIS |
| Storage | S3 compatible object storage (MinIO locally, Amazon S3 in the cloud), ClamAV upload scanning |
| Frontend | React 19, TypeScript, Vite, Tailwind, Fluent UI |
| API | OpenAPI 3.1 contract in `packages/api-contracts`, typed client generated for the frontend |
| Testing | JUnit, Testcontainers, ArchUnit, Vitest, Testing Library, MSW |
| Infra | Docker Compose, AWS EC2, CloudFront, S3, Systems Manager |
| CI | GitHub Actions quality gate covering tests, formatting, linting, secret scanning, dependency checks and breaking API changes |

## How it's put together

```
apps/
  backend/     Spring Boot modular monolith (one module per business area)
  frontend/    React single page app
packages/
  api-contracts/   OpenAPI contract shared by both apps
infra/
  containers/  Docker Compose for local and AWS
  aws/         Bootstrap and deploy scripts
docs/
  adr/         Architecture decision records
  deployment/  AWS deployment guide
  operations/  Demo readiness and provider setup
```

The backend is a modular monolith. Each business area (access, procurement, transport, routing, shipment, telemetry, handover, payment, trust and so on) lives in its own module. ArchUnit tests stop modules from reaching into each other's internals. Modules talk through a transactional outbox, so events don't get lost if something fails halfway.

External providers like payments, OTP, maps, document extraction and messaging sit behind adapters. Each one has a local or mock version, so the whole journey runs offline without spending provider credits. Uploads fail closed: if no scanner is configured, they get rejected.

Some decisions worth reading:

- [ADR 0002](docs/adr/0002-postgis-provisioning.md): why the PostGIS migration requires the extension instead of creating it
- [ADR 0003](docs/adr/0003-backup-and-restore.md): backup and restore
- [ADR 0005](docs/adr/0005-key-rotation.md): key rotation

## Running it locally

You need Docker, Java 21 and Node 24.

**1. Start the stack**

```bash
cp infra/containers/.env.example infra/containers/.env
# fill in the blank secrets in .env (Compose refuses to start without them)
docker compose -f infra/containers/docker-compose.yml up -d
```

This starts the backend, PostgreSQL with PostGIS, MinIO and ClamAV. The API runs on `http://localhost:8080`.

**2. Start the frontend**

```bash
cd apps/frontend
cp .env.example .env
npm ci
npm run dev
```

**3. Run the tests**

```bash
# backend: compile, format check and every test (Docker must be running for Testcontainers)
cd apps/backend
./mvnw verify

# frontend: API client check, format, lint, types, tests and build
cd apps/frontend
npm run verify
```

## The demo journey

`scripts/run-demo-journey.ps1` runs the whole flow twice on clean data with the local adapters:

1. OTP login and Mobile Money consent
2. Product request, supplier invite, document extraction and a deliberate document mismatch
3. Demand grouping, capacity matching and route selection
4. Delivery proposal, confirmation and escrow lock
5. Live tracking with a route deviation and a fuel drop
6. A QR handover scan with the wrong quantity
7. Payment blocked, dispute resolved, payment released
8. Notifications, an updated trust score, a new premium estimate and insurer evidence

If both runs pass, the demo is good to go. See [demo readiness](docs/operations/demo-readiness.md) for the live provider checks.

## Deployment

The backend runs on a single EC2 instance behind CloudFront in `af-south-1`. There's no SSH and no open database ports, and only CloudFront can reach the instance. The full setup, including which providers are real and which are mocked, is in [docs/deployment/aws-ec2.md](docs/deployment/aws-ec2.md).

This setup is sized for a demo, not for high availability.

## Status

Hackathon MVP. A few providers (company registry, document extraction, email) are still mocked in the cloud deployment, and production would need versioned key rings and a tested restore before any real data goes through it.

## Team

- [Kea Nhlapo](https://github.com/Kea-nhlapo)
- [Bandile](https://github.com/bandilleee)
- [Tshegofatso Mkhabela](https://github.com/TshegofatsoMkhabela)
- [Lungile Mooketsi](https://github.com/lungilemooketsi)
