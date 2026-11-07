#!/bin/bash
# Runs on the server (GitHub Actions over SSH, or by hand): update to main, rebuild, health check, roll back on failure
set -euo pipefail
cd "$(dirname "$0")/.."

HEALTH_URL=http://localhost/health

rollback() {
  echo "deploy failed: $1, rolling back" >&2
  if docker image inspect yong-app:prev >/dev/null 2>&1; then
    docker tag yong-app:prev yong-app:latest
    docker compose up -d --no-build app
  fi
  exit 1
}

if docker image inspect yong-app:latest >/dev/null 2>&1; then
  docker tag yong-app:latest yong-app:prev
fi

# Always deploy main, even if the server was left on another branch
git fetch -q origin main
git checkout -q main
git merge -q --ff-only origin/main
echo "deploying $(git rev-parse --short HEAD)"

# up waits for app to be healthy before starting nginx, so a broken app fails here
docker compose up -d --build || rollback "compose up"
# nginx is not recreated when only nginx.conf changes
docker compose exec -T nginx nginx -s reload || rollback "nginx reload"

for _ in $(seq 1 12); do
  if curl -fsS -o /dev/null "$HEALTH_URL"; then
    echo "deploy ok: $(git rev-parse --short HEAD)"
    exit 0
  fi
  sleep 5
done
rollback "health check"
