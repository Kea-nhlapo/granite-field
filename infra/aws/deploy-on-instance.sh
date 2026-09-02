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
HEALTH=http://127.0.0.1:8080/actuator/health

cd "$APP_DIR"

# The compose file and .env template ship with the code, so the checkout has to
# move in step with the image or they disagree about what the app needs.
git fetch origin --prune
git checkout main
git reset --hard "$NEW_TAG"

cd infra/containers

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

echo "releasing ${IMAGE_REPO}:${NEW_TAG}"
release "${IMAGE_REPO}:${NEW_TAG}"

if wait_for_health; then
  echo "$NEW_TAG" > /opt/trademesh/current-tag.txt
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
