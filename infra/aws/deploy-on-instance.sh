#!/usr/bin/env bash
# Runs ON the EC2 instance, sent by the deploy workflow through SSM.
#
# Takes one argument: the image tag to release (the commit SHA).
#
# The contract: this script either leaves the instance healthy on the new image,
# or healthy on the one it was already running. It never leaves it down.
set -euo pipefail

NEW_TAG="${1:?usage: deploy-on-instance.sh <image-tag>}"
REGISTRY=698251583462.dkr.ecr.af-south-1.amazonaws.com
IMAGE_REPO="${REGISTRY}/trademesh-backend"
APP_DIR=/opt/trademesh/granite-field
COMPOSE="docker compose -f docker-compose.aws.yml --env-file .env"
# Readiness, not the aggregate: it includes the database and the object store, so a
# release that cannot reach its bucket fails here and rolls back instead of shipping.
HEALTH=http://127.0.0.1:8080/actuator/health/readiness

# The host was provisioned with git, openssl and Docker only. Fetching an ECR
# login token needs the AWS CLI, so install it if this host predates that need.
if ! command -v aws >/dev/null 2>&1; then
  # Ubuntu 24.04 has no awscli package: v1 was dropped from the archive and AWS
  # ships v2 as its own installer. Use that rather than an apt package that does
  # not exist on this release.
  echo "installing the AWS CLI v2"
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl unzip
  curl -fsSL https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip
  unzip -q -o /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install --update
  rm -rf /tmp/aws /tmp/awscliv2.zip
  aws --version
fi

# The watchdog must not fight the deploy: a backend that is still starting looks
# exactly like a backend that has hung. Hold the lock for the whole release.
mkdir -p /opt/trademesh
touch /opt/trademesh/deploying
trap 'rm -f /opt/trademesh/deploying' EXIT

cd "$APP_DIR"

# The compose file and .env template ship with the code, so the checkout has to
# move in step with the image or they disagree about what the app needs.
git fetch origin --prune
git checkout main
git reset --hard "$NEW_TAG"

cd infra/containers

# .env is created once from the example and then belongs to the host, because it
# holds this deployment's secrets. Settings added to the example afterwards would
# never reach it, so a release that introduces a required variable fails on its
# own change. Copy across any key .env does not have yet, and never touch a key
# it already has - the secrets in there must survive untouched.
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  key="${line%%=*}"
  if ! grep -q "^${key}=" .env; then
    echo "adding new setting from the example: ${key}"
    printf '%s\n' "$line" >> .env
  fi
done < .env.aws.example

# A secret the example ships blank cannot arrive by being copied, and waiting for
# somebody to log in and paste one is how a release stays down for an hour. These
# belong to the host and nothing outside it needs to know them, so generate them
# here. A key that already has a value is never touched: rotating a live signing
# secret would invalidate every token issued under it.
for key in HANDOVER_QR_SIGNING_SECRET; do
  if grep -q "^${key}=$" .env; then
    echo "generating a value for ${key}"
    # Hex, so the value cannot contain a character that breaks sed or .env parsing.
    sed -i "s|^${key}=$|${key}=$(openssl rand -hex 32)|" .env
  fi
done

# Everything above only edits files. This is the last point at which the release
# can be abandoned with the running stack untouched, so check here that the
# compose file and .env agree before anything is stopped or migrated.
$COMPOSE config --quiet

PREVIOUS_IMAGE="$(grep '^BACKEND_IMAGE=' .env | cut -d= -f2-)"
echo "previous image: ${PREVIOUS_IMAGE:-none}"

aws ecr get-login-password --region af-south-1 \
  | docker login --username AWS --password-stdin "$REGISTRY"

# Taken before the new image starts, because Flyway migrations run at startup
# and are forward-only. Rolling the image back does not roll the schema back.
mkdir -p /opt/trademesh/backups
if $COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
     > "/opt/trademesh/backups/pre-${NEW_TAG}.sql" 2>/dev/null; then
  echo "database dumped to /opt/trademesh/backups/pre-${NEW_TAG}.sql"
else
  echo "WARNING: pre-deploy dump failed; continuing"
fi

# Releases accumulate one image each. A full disk takes down PostgreSQL, clamd and
# the backend at once and looks like a mystery outage, so keep only what a rollback
# could need: the tag just released, the one before it, and "latest".
prune_old_images() {
  docker images --format '{{.Repository}}:{{.Tag}}' "$IMAGE_REPO" 2>/dev/null \
    | grep -v -e ":${NEW_TAG}$" -e ':latest$' -e "^${PREVIOUS_IMAGE}$" \
    | xargs -r docker rmi -f >/dev/null 2>&1 || true
  docker image prune -f >/dev/null 2>&1 || true
}

# "restart: unless-stopped" covers a process that dies. It does nothing for a JVM
# that is alive but wedged, which is the failure that quietly loses a demo. This
# timer notices that case and restarts the backend; it stands down during releases
# and for the first five minutes of a container's life, when a slow start is normal.
install_watchdog() {
  cat > /opt/trademesh/watchdog.sh <<'WATCHDOG'
#!/usr/bin/env bash
set -u
# Liveness only. This restarts a process that has stopped responding; a database or
# object-store outage is not something a container restart fixes.
HEALTH=http://127.0.0.1:8080/actuator/health/liveness
cd /opt/trademesh/granite-field/infra/containers || exit 0

[ -f /opt/trademesh/deploying ] && exit 0
curl -fsS --max-time 10 "$HEALTH" >/dev/null 2>&1 && exit 0

# One bad probe is not an outage. Confirm before touching anything.
sleep 20
[ -f /opt/trademesh/deploying ] && exit 0
curl -fsS --max-time 10 "$HEALTH" >/dev/null 2>&1 && exit 0

started=$(docker inspect -f '{{.State.StartedAt}}' trademesh-backend 2>/dev/null) || exit 0
age=$(( $(date +%s) - $(date -d "$started" +%s 2>/dev/null || echo 0) ))
[ "$age" -lt 300 ] && exit 0

logger -t trademesh-watchdog "backend unhealthy for over 5 minutes; restarting"
docker compose -f docker-compose.aws.yml --env-file .env up -d --force-recreate backend
WATCHDOG
  chmod 750 /opt/trademesh/watchdog.sh

  cat > /etc/systemd/system/trademesh-watchdog.service <<'UNIT'
[Unit]
Description=Restart the TradeMesh backend if it stops answering its health check

[Service]
Type=oneshot
ExecStart=/opt/trademesh/watchdog.sh
UNIT

  cat > /etc/systemd/system/trademesh-watchdog.timer <<'UNIT'
[Unit]
Description=Check the TradeMesh backend every two minutes

[Timer]
OnBootSec=10min
OnUnitActiveSec=2min
AccuracySec=15s

[Install]
WantedBy=timers.target
UNIT

  systemctl daemon-reload
  systemctl enable --now trademesh-watchdog.timer >/dev/null 2>&1 || true
}

release() {
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=$1|" .env
  # Not fatal: the very first rollback target is the image that was built on the
  # host before ECR existed, so it lives only in the local daemon and cannot be
  # pulled. "up -d" uses the local copy when the pull finds nothing.
  $COMPOSE pull backend || echo "pull found nothing remote; using local image"
  $COMPOSE up -d
}

wait_for_health() {
  for _ in $(seq 1 36); do
    if curl -fsS --max-time 5 "$HEALTH" >/dev/null 2>&1; then
      return 0
    fi
    sleep 10
  done
  return 1
}

# Docker restarts containers on crash and on boot only if the daemon itself comes
# back, and a host that reboots with Docker disabled comes back with nothing.
systemctl enable docker >/dev/null 2>&1 || true

install_watchdog

echo "releasing ${IMAGE_REPO}:${NEW_TAG}"
release "${IMAGE_REPO}:${NEW_TAG}"

if wait_for_health; then
  echo "$NEW_TAG" > /opt/trademesh/current-tag.txt
  prune_old_images
  echo "DEPLOY_OK ${NEW_TAG}"
  exit 0
fi

echo "new image did not become healthy within 6 minutes"
$COMPOSE logs --tail=60 backend || true

if [ -z "$PREVIOUS_IMAGE" ]; then
  echo "ROLLBACK_IMPOSSIBLE no previous image recorded"
  exit 1
fi

echo "rolling back to ${PREVIOUS_IMAGE}"
release "$PREVIOUS_IMAGE"

if wait_for_health; then
  echo "ROLLED_BACK healthy on ${PREVIOUS_IMAGE}"
else
  echo "ROLLBACK_FAILED service is down, manual intervention needed"
fi
exit 1
