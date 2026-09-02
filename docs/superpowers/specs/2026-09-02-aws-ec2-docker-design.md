# AWS EC2 Docker Deployment Design

## Objective

Deploy the current TradeMesh backend to AWS with the fewest moving parts while preserving a clear path to ECS Fargate later. One EC2 instance will run the backend, PostgreSQL/PostGIS, and MinIO with Docker Compose.

The deployment is intended for hackathon development and demonstration. It is not a highly available production topology.

## Architecture

One x86-64 EC2 instance runs three containers on a private Compose network:

- `backend`: the Spring Boot application built with Java 21.
- `postgres`: the existing PostgreSQL 17 and PostGIS 3.5 image.
- `minio`: the existing S3-compatible object store.

Only the backend port is published publicly. PostgreSQL and MinIO remain reachable only from the EC2 host and the Compose network. Docker named volumes persist PostgreSQL and MinIO data on the instance's EBS root volume.

The first deployment uses the instance's public address and port 8080. A domain, TLS termination, load balancer, managed RDS database, and managed object storage are deferred.

## Repository Changes

### Backend image

Add `apps/backend/Dockerfile` as a multi-stage build:

1. A Java 21 Maven build stage uses the checked-in Maven wrapper to create the executable JAR.
2. A smaller Java 21 runtime stage copies only the JAR.
3. The runtime process executes as an unprivileged user.
4. The container exposes port 8080 and includes an Actuator health check.

Add `apps/backend/.dockerignore` so builds exclude Maven output, IDE metadata, local environment files, and unrelated workspace content.

### Compose stack

Extend `infra/containers/docker-compose.yml` with a `backend` service that:

- builds from the repository's backend Dockerfile;
- connects to PostgreSQL through the Compose service name `postgres`;
- connects to MinIO through the Compose service name `minio`;
- receives secrets and configurable values from environment variables;
- depends on healthy PostgreSQL and a started MinIO service;
- publishes `${BACKEND_PORT:-8080}:8080`;
- uses a restart policy suitable for a single-instance demonstration deployment; and
- has a health check against `/actuator/health`.

The existing loopback-only host bindings for PostgreSQL and MinIO remain unchanged. They must not be exposed through the EC2 security group.

### Deployment documentation

Add a short AWS EC2 deployment guide covering:

- an x86-64 Ubuntu EC2 instance with enough memory for the three services;
- Docker Engine and Docker Compose installation;
- repository checkout;
- creation of an uncommitted deployment environment file;
- image build and Compose startup;
- security-group access to the backend port;
- health verification; and
- log inspection, restart, update, backup, and shutdown commands.

The guide will recommend AWS Systems Manager Session Manager or tightly restricted SSH administration. It will explicitly warn that the first deployment is HTTP-only and should not process real confidential data.

## Configuration and Secrets

No AWS credentials or application secrets will be committed. The EC2 host supplies at least:

- `AUTH_JWT_SECRET`
- `NOTIFICATION_DATA_ENCRYPTION_KEY`
- `POSTGRES_PASSWORD`
- `OBJECT_STORAGE_ACCESS_KEY`
- `OBJECT_STORAGE_SECRET_KEY`

Compose derives the backend database and object-storage connection settings from the same deployment environment. Container-to-container endpoints use Compose DNS names, never `localhost`.

The Spring profile remains `local` for this minimal topology because the existing non-local configuration expects separately provisioned infrastructure. Secrets still override every local default. Migration to a dedicated AWS profile is deferred to the managed-services deployment.

## Startup and Data Flow

1. Compose starts PostgreSQL and MinIO.
2. PostgreSQL reports healthy.
3. Compose starts the backend with environment-derived connection settings.
4. Spring Boot runs Flyway migrations and verifies that PostGIS is present.
5. The backend reports readiness through `/actuator/health`.
6. Requests arrive on the EC2 public address and port 8080.

PostgreSQL and MinIO data survive container recreation while the EC2 instance and its EBS volume remain intact. Instance or volume loss is outside this topology's availability guarantee.

## Failure Handling

- A failed image build stops deployment before containers are replaced.
- If PostgreSQL is unhealthy, the backend does not start successfully.
- If Flyway or PostGIS validation fails, the backend remains unhealthy and logs the cause.
- Docker restarts the backend after an unexpected process exit.
- Operators use `docker compose ps`, health status, and container logs for diagnosis.
- Before destructive container or volume operations, operators back up PostgreSQL and MinIO data.

## Verification

Repository verification will include:

- building the backend image from a clean Docker context;
- starting the full Compose stack;
- confirming all three services remain running;
- checking the backend Actuator health endpoint from the host;
- confirming Flyway connects to the PostGIS database;
- confirming PostgreSQL and MinIO ports remain loopback-only;
- running the existing backend quality gate with Java 21 where available; and
- validating the Compose configuration before deployment.

## Explicit Non-Goals

- ECS Fargate, ECR, RDS, or native S3 migration
- infrastructure as code
- automatic CI/CD deployment
- autoscaling or multi-instance availability
- an Application Load Balancer
- domain registration or TLS certificates
- production backup automation and disaster recovery
- frontend deployment

These can be added after the hackathon deployment proves the container and configuration boundaries.
