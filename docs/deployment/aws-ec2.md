# Minimal AWS EC2 Deployment

This deployment runs the TradeMesh backend, PostgreSQL/PostGIS, and MinIO on one EC2 instance. It is intended for hackathon development and demonstrations, not highly available production use.

## 1. Create the instance

Create an x86-64 Ubuntu 24.04 LTS EC2 instance with:

- a `t3.medium` instance type;
- at least 20 GiB of gp3 storage;
- a public IPv4 address; and
- an EC2 security group allowing TCP port 8080 from the demo audience.

For administration, prefer [AWS Systems Manager Session Manager](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-with-systems-manager-session-manager.html). If you use it, attach an instance profile with the `AmazonSSMManagedInstanceCore` policy when creating the instance. If SSH is necessary, allow port 22 only from your current public IP. Do not expose ports 5432, 9000, or 9001 in the security group.

The initial endpoint uses unencrypted HTTP. Do not process real confidential, personal, financial, or production data through it. Add a domain and TLS termination before using it beyond a controlled demonstration.

## 2. Install the host tools

Connect to the instance and install Git, OpenSSL, Docker Engine, and the Compose plugin:

```bash
sudo apt-get update
sudo apt-get install -y git openssl docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Reconnect so the new group membership takes effect, then verify the installation:

```bash
docker --version
docker compose version
```

Membership in the `docker` group grants root-equivalent control of the host. Limit it to the deployment administrator and use AWS access controls to protect the instance.

## 3. Check out the repository

Clone the private repository using an SSH deploy key or another short-lived GitHub credential. Do not place an access token directly in a command or remote URL.

```bash
git clone git@github.com:Kea-nhlapo/granite-field.git
cd granite-field/infra/containers
```

## 4. Create deployment secrets

Create the ignored Compose environment file and restrict access:

```bash
cp .env.example .env
chmod 600 .env
```

Generate separate random values and paste them into the corresponding blank entries in `.env`:

```bash
openssl rand -base64 48
openssl rand -base64 32
openssl rand -hex 24
openssl rand -base64 32
```

Use the values in order for `AUTH_JWT_SECRET`, `NOTIFICATION_DATA_ENCRYPTION_KEY`, `POSTGRES_PASSWORD`, and `OBJECT_STORAGE_SECRET_KEY`. Do not reuse a value between settings. Leave `OBJECT_STORAGE_ACCESS_KEY` as a non-secret account name or replace it with another non-sensitive identifier.

## 5. Validate and start the stack

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
```

The initial image build downloads Java and Maven dependencies and can take several minutes. The backend starts only after PostgreSQL is healthy, then Flyway verifies PostGIS and applies the schema.

Check the service from the instance:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

Then check `http://EC2_PUBLIC_IP:8080/actuator/health` from your workstation. A successful response contains `"status":"UP"`.

## 6. Operate and update

Inspect status and logs:

```bash
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 backend
```

Deploy a newer commit:

```bash
git pull --ff-only
docker compose --env-file .env up -d --build
```

Restart only the backend:

```bash
docker compose --env-file .env restart backend
```

Stop the stack while keeping its named volumes:

```bash
docker compose --env-file .env down
```

Do not add `--volumes` to the shutdown command unless permanent deletion of the database and object store is intentional.

## 7. Back up before destructive work

Create a PostgreSQL dump:

```bash
docker compose --env-file .env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > trademesh.sql
```

Store backups outside the instance. An EBS snapshot can protect both Docker named volumes, but application-consistent snapshots require stopping writes or the stack first. By default, terminating the EC2 instance may delete its root EBS volume, so instance termination is not a backup strategy.

## Known limitation

MinIO remains bound to the EC2 loopback interface. Uploads sent through the backend work, but generated MinIO presigned download URLs contain the Compose-only `minio` hostname and are not usable by remote clients in this minimum topology. Exposing downloads requires a public storage endpoint or migration to Amazon S3; that belongs to the managed AWS deployment rather than this minimal backend demonstration.
