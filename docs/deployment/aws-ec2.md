# AWS deployment

This deploys the TradeMesh backend to a single EC2 instance behind CloudFront, with
Amazon S3 for object storage and clamd scanning uploads. It is sized for hackathon
demonstration, not for high availability.

Everything below has been executed against account `698251583462` in `af-south-1`.

## Topology

```
        Internet
           |  HTTPS (CloudFront certificate, no domain required)
   CloudFront distribution
     |                     |
     /  -> S3 site bucket  /api/* -> EC2 :8080
                                      |
                            backend + postgres + clamav (Docker)
                                      |
                            Amazon S3 (uploads bucket)
```

Only CloudFront can reach the instance: the security group allows TCP 8080 from the
`com.amazonaws.global.cloudfront.origin-facing` managed prefix list and nothing else.
There is no SSH and no key pair. Administration is AWS Systems Manager Session Manager.

PostgreSQL and clamd publish no host ports at all. They are reachable only over the
Compose network.

## What is real and what is mocked

Read this before putting anything but demonstration data through the deployment.

| Concern | Cloud setting | Meaning |
| --- | --- | --- |
| Upload scanning | `FILE_SCANNER_PROVIDER=clamav` | Real clamd. Transport or protocol failure returns ERROR and the upload is rejected. |
| Company registry | `COMPANY_REGISTRY_PROVIDER=mock` | **Invented CIPC records.** Deliberate for the demo. Replace before real onboarding. |
| Document extraction | `DOCUMENT_EXTRACTION_PROVIDER=mock` | Not a real extraction service. |
| Email | `EMAIL_PROVIDER=local` | Captured locally, not delivered. |
| Origin transport | HTTP | CloudFront terminates TLS. CloudFront-to-instance traffic is HTTP inside AWS. |

Both provider settings are properties, not Spring profiles, so what is running is visible
in `.env`. Leaving `FILE_SCANNER_PROVIDER` unset resolves to `fail-closed` and uploads are
refused. That is intentional: a forgotten setting must never downgrade to a mock scanner.

**Never set `SPRING_PROFILES_DEFAULT=local` in a deployment.** The `local` profile supplies
fallback database credentials and a committed notification encryption key that exists in the
public repository.

## 1. AWS resources

Two S3 buckets, both with public access blocked and AES256 encryption:

- `trademesh-uploads-<account-id>` — application uploads
- `trademesh-site-<account-id>` — the built frontend, read by CloudFront through an
  Origin Access Control

An IAM user for the backend, with an inline policy scoped to the uploads bucket only:
`s3:ListBucket` and `s3:GetBucketLocation` on the bucket, `s3:GetObject`, `s3:PutObject`
and `s3:DeleteObject` on its contents. Nothing else in the account.

An IAM role and instance profile `trademesh-ec2` with `AmazonSSMManagedInstanceCore`.

A `t3.large` running Ubuntu 24.04 with a 30 GiB encrypted gp3 root volume, IMDSv2
required, and the instance profile attached. `t3.medium` is not enough: clamd needs
roughly 1.5 GiB for its signature database alongside the JVM and PostgreSQL.

## 2. Host packages

Supplied by instance user-data, and safe to re-run:

```bash
sudo apt-get update
sudo apt-get install -y git openssl docker.io docker-compose-v2 curl unzip
sudo systemctl enable --now docker

# Ubuntu 24.04 carries no awscli package; the release script installs v2 from
# AWS directly if it is missing. To do it by hand:
curl -fsSL https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip
unzip -q -o /tmp/awscliv2.zip -d /tmp && sudo /tmp/aws/install --update
```

## 3. Check out the repository

```bash
sudo install -d -o ubuntu -g ubuntu /opt/trademesh
cd /opt/trademesh
git clone https://github.com/Kea-nhlapo/granite-field.git
cd granite-field/infra/containers
```

## 4. Deployment secrets

Copy the **cloud** example, not the local one:

```bash
cp .env.aws.example .env
chmod 600 .env
```

Generate a separate value for each secret on the host. Never reuse one:

```bash
openssl rand -base64 48   # AUTH_JWT_SECRET
openssl rand -base64 32   # NOTIFICATION_DATA_ENCRYPTION_KEY
openssl rand -hex 24      # POSTGRES_PASSWORD
```

Set `OBJECT_STORAGE_BUCKET` to the uploads bucket, and paste the IAM user's access key
into `OBJECT_STORAGE_ACCESS_KEY` and `OBJECT_STORAGE_SECRET_KEY`.

Confirm every storage setting has a value without printing any of them:

```bash
sudo sed -n 's/^\(OBJECT_STORAGE_[A-Z_]*\)=\(..*\)/\1 SET/p' .env
```

Five lines is correct: endpoint, region, bucket, access key, secret key.

## 5. Releases are automatic

Once `infra/aws/bootstrap-cd.sh` has been run and the `AWS_DEPLOY_ROLE`
repository variable is set, every commit that reaches `main` and passes Quality
Gate is released without anyone touching the instance:

1. GitHub Actions assumes an AWS role through OIDC. No access key is stored in
   the repository.
2. The backend image is built in CI and pushed to ECR, tagged with the commit
   SHA. The instance never compiles anything.
3. `infra/aws/deploy-on-instance.sh` runs over Systems Manager: it dumps the
   database, records the running image, pulls the new tag and restarts.
4. It polls `/actuator/health` for six minutes. If the new image does not become
   healthy it redeploys the previous tag and the workflow fails red.
5. The frontend is built, synced to the site bucket and CloudFront is invalidated.

**Rolling back the image does not roll back the database.** Flyway migrations run
at startup and are forward-only, so a release that migrates the schema and then
fails leaves the old image facing a newer schema. That is why the script takes a
dump first, into `/opt/trademesh/backups/pre-<sha>.sql` on the instance. A
migration that drops or rewrites a column needs a deliberate plan, not the
automatic rollback.

To release a specific commit by hand, run the Deploy workflow from the Actions
tab. To go back to a known-good build, set `BACKEND_IMAGE` in `.env` to that tag
and run the commands below.

## 6. Starting the stack by hand

Every command uses `-f docker-compose.aws.yml`. Omitting it silently runs the **local
development stack**, which uses MinIO and the mock scanner.

```bash
docker compose -f docker-compose.aws.yml --env-file .env config --quiet
docker compose -f docker-compose.aws.yml --env-file .env pull
docker compose -f docker-compose.aws.yml --env-file .env up -d
docker compose -f docker-compose.aws.yml --env-file .env ps
```

The backend image comes from ECR; only PostGIS and clamd are pulled from Docker Hub.
The backend waits for clamd to report healthy, which takes a few minutes on a cold start
while the signature database loads. All three services should reach `(healthy)`.

Verify from the instance:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

Then through CloudFront. An unauthenticated `/api/**` request returning 401 from Spring
confirms the whole path works; a CloudFront or S3 error page means it does not.

## 7. Frontend by hand

```bash
cd apps/frontend && npm ci && npm run build
aws s3 sync dist/ s3://trademesh-site-<account-id>/ --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

## 8. Operate

```bash
docker compose -f docker-compose.aws.yml --env-file .env logs --tail=200 backend
docker compose -f docker-compose.aws.yml --env-file .env restart backend
git pull --ff-only && docker compose -f docker-compose.aws.yml --env-file .env up -d
docker compose -f docker-compose.aws.yml --env-file .env down
```

Do not add `--volumes` to `down` unless deleting the database is intended.

Back up before destructive work, and store the dump off the instance:

```bash
docker compose -f docker-compose.aws.yml --env-file .env exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > trademesh.sql
```

Terminating the instance is not a backup. Stop it between sessions instead: the named
volumes persist on the EBS volume and `restart: unless-stopped` brings the stack back
on boot.

## Cost

A `t3.large` is roughly USD 0.10 per hour, about USD 70 per month if left running.
Stop it when the team is not working.
