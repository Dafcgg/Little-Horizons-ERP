#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
container_name="little-horizons-test-$$"
cleanup() { docker rm -f -v "$container_name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# Disposable local credentials, used only inside this test container.
docker run --name "$container_name" -e MYSQL_ROOT_PASSWORD=erp_test_only -e MYSQL_ROOT_HOST=% \
  -p 127.0.0.1::3306 -d mysql:8.0 >/dev/null
ready=false
for attempt in $(seq 1 60); do
  if docker exec -e MYSQL_PWD=erp_test_only "$container_name" mysql -h127.0.0.1 -uroot -Nse 'SELECT 1' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  docker logs "$container_name"
  exit 1
fi
docker exec -i -e MYSQL_PWD=erp_test_only "$container_name" mysql -uroot --default-character-set=utf8mb4 < docs/database.sql
endpoint=$(docker port "$container_name" 3306/tcp)
export ERP_TEST_DB_URL="jdbc:mysql://127.0.0.1:${endpoint##*:}/erp_little_horizons?serverTimezone=America/Bogota"
export ERP_TEST_DB_USER=root
export ERP_TEST_DB_PASSWORD=erp_test_only
mvn clean verify
