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
| Bot challenge | `TURNSTILE_PROVIDER=local` | Accepts any challenge. No Cloudflare check. |
| One-time passwords | `OTP_PROVIDER=local` | Fixed local code. No SMS is sent. |
| Mobile money | `MOMO_PROVIDER=mock` | No real payment is initiated. |
| Speech and distance | `..._PROVIDER=local` | Local stand-ins, not Google. |
| Mobile notifications | `MOBILE_NOTIFICATION_PROVIDER=infobip` | Real consent-aware SMS and WhatsApp through Infobip, with signed delivery and seen callbacks. |
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

Complete the Infobip settings and subscriptions before the first release. The exact
sender, template, callback, rotation, and verification procedure is in
[`docs/operations/infobip-notifications.md`](../operations/infobip-notifications.md).

Confirm every storage setting has a value without printing any of them:

```bash
sudo sed -n 's/^\(OBJECT_STORAGE_[A-Z_]*\)=\(..*\)/\1 SET/p' .env
```

Five lines is correct: endpoint, region, bucket, access key, secret key.

## 5. Releases are automatic

Work happens on `dev`. Feature branches open pull requests into `dev`, and `dev`
opens a pull request into `main` when the team is ready to release. Quality Gate
runs on both. `main` is the only branch wired to the instance, so nothing reaches
AWS until someone deliberately merges into it.

There is one workflow, `.github/workflows/quality-gate.yml`. Its `deploy` job
declares `needs: [backend, frontend, secrets, api-contract]`, so GitHub will not
start a release unless every check above it is green — the gate is not a
convention, it is a dependency. The green tick on a commit on `main` therefore
means "tested **and** released", and there is no second workflow run to go
looking for when something fails.

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

To re-release the tip of `main` by hand, run Quality Gate from the Actions tab
with `main` selected. To go back to a known-good build, set `BACKEND_IMAGE` in
`.env` to that tag and run the commands below.

### A missing setting costs one feature, not the service

Every provider the application selects by configuration has a bean registered for
the value `unconfigured`, and that bean is what Spring uses when nothing is set.
The application therefore always starts; a feature nobody has wired throws a clear
error the first time somebody calls it. Nothing in `docker-compose.aws.yml`
requires a provider to be chosen either — only genuine secrets are mandatory.

This matters because `.env` lives on the host and is not in git. Without it, any
new feature merged to `main` that reads a new setting takes the whole backend down
on the next release, and the image rollback cannot recover it because both images
read the same `.env`. The release script still copies newly added keys across from
`.env.aws.example`, but that is now a convenience rather than the thing standing
between the team and an outage.

### The CloudFront distribution

`E1VXUDQWO5B99P`, serving `dbhptzazg1vi1.cloudfront.net`. The distribution id
appears in `infra/aws/bootstrap-cd.sh` and in the workflow's `DISTRIBUTION_ID`.
Both were once transcribed wrong, which surfaces as `NoSuchDistribution` on the
very last step of a release that otherwise succeeded: the backend is out, the
site files are uploaded, and only the cache flush failed. Read the id from the
console rather than from memory if it ever needs to change.

Because the backend is verified before the CDN is touched, that failure mode now
fails the workflow without leaving any doubt about whether the release is
serving.

### Where credentials live

There are two stores and they do not overlap.

**GitHub repository secrets** are handed to the workflow runner, which exists for
about three minutes per release. They are never baked into the image.

**`infra/containers/.env` on the instance** is what the running application reads.
It is not in git and it survives releases.

A repository secret is treated as application configuration unless it is named
`CI_*`, which marks it as belonging to the pipeline. Everything else is published
to AWS Parameter Store under `/trademesh/` as a SecureString during the release,
and `deploy-on-instance.sh` writes those into `.env` before starting the new
image. Parameter Store owns the keys it holds and overwrites them every release;
every other key in `.env` - the generated secrets, the provider choices - is left
alone. Adding an integration credential is therefore a GitHub change and nothing
else.

The values go through Parameter Store rather than straight into the release
command because Systems Manager retains the text of every command it runs.

Two things this does **not** do, both of which have bitten this project already:

* **Credentials do not switch a feature on.** `MOMO_PROVIDER` and
  `MOBILE_NOTIFICATION_PROVIDER` select the stand-ins by default and keep doing so
  no matter what keys arrive. Set them to `http` and `twilio` deliberately, and
  expect real messages and real sandbox transactions from that moment.

The backend service passes the whole `.env` through with `env_file` rather than
naming variables one at a time. That was not always true, and it is why every
`MOMO_*` and `TWILIO_*` credential could have sat on the host without the
container ever seeing it: the value was present, the pass-through was not, and
nothing reported it. A new integration now needs its repository secret and
nothing else - no compose change, no entry in the example.

A secret whose name nothing reads is carried to the host and harmlessly ignored,
which is the useful case for work in progress: `INFOBIP_*` is already on its way
to the instance, waiting for the provider that will read it.

### Storage is checked, not assumed

The object storage client is lazy: it connects on the first upload, not at
startup. An endpoint, region, bucket or key that is wrong therefore used to be
invisible until a user tried to upload a document.

`objectStorage` is now a readiness health indicator, so `/actuator/health/readiness`
answers whether the bucket actually responds to the configured credentials. The
release script polls readiness rather than the aggregate, which means a deployment
that cannot reach its bucket fails and rolls back instead of shipping.

The container healthcheck and the watchdog poll `/actuator/health/liveness`
instead. A storage or database outage should stop a release; it should not restart
a process that is running perfectly well and can do nothing about it.

The indicator is switched off in the backend test suite
(`management.health.object-storage.enabled=false`). Those tests have no object
store, and a check that told the truth there would report DOWN and fail every test
that asserts the application is healthy - for a reason that has nothing to do with
the application.

Cloud Readiness runs against a real scratch bucket rather than placeholder keys,
created by `infra/aws/bootstrap-ci-storage.sh` along with a user whose only
permission is that bucket. Its objects expire after a day. The three
`CI_OBJECT_STORAGE_*` repository secrets come from that script's output; without
them the job fails and says so.

### Keeping it up

* `restart: unless-stopped` on all three services, and `docker` is enabled at
  boot, so a reboot brings the whole stack back.
* A systemd timer, installed by the release script, checks `/actuator/health`
  every two minutes and recreates the backend if it has been unhealthy for more
  than five minutes. It stands down during a release and while a container is
  still starting. Docker's restart policy covers a process that dies; this covers
  a JVM that is alive but wedged. Check it with
  `systemctl list-timers trademesh-watchdog` and
  `journalctl -t trademesh-watchdog`.
* Each release deletes backend images other than the one just released, the one
  before it, and `latest`. A full disk stops PostgreSQL, clamd and the backend at
  the same time and is easy to misread as something more interesting.

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
