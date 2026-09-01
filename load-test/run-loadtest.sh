#!/usr/bin/env bash
#
# End-to-end SwiftPay load test  (spec: 250 TPS, 1,000,000 transactions).
#
#   1. verify the docker-compose stack is healthy
#   2. seed 10k funded accounts, warm the JVMs, then truncate for a 0 baseline
#   3. start a full-mesh packet capture on the Docker bridge (API+Kafka+PG+Redis)
#   4. drive 250 req/s x 4000 s with k6  (= 1,000,000 POST /v1/payments)
#   5. stop the capture, reconcile gateway -> ledger -> analytics, gzip the pcap
#
# Prereqs: colima + docker, k6, an already-built & running stack
#          (docker compose up --build -d), ~10 GB free in the colima VM.
#
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$REPO_ROOT"
ART="load-test/artifacts"; mkdir -p "$ART"
export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"

RATE="${RATE:-250}"
DURATION_S="${DURATION_S:-4000}"
WARMUP_S="${WARMUP_S:-300}"

pgc()  { docker exec swiftpay-postgres-gateway   psql -U swiftpay -d swiftpay_gateway    -tAc "$1"; }
plc()  { docker exec swiftpay-postgres-ledger    psql -U swiftpay -d swiftpay_ledger     -tAc "$1"; }
pac()  { docker exec swiftpay-postgres-analytics psql -U swiftpay -d swiftpay_analytics  -tAc "$1"; }
seed() { docker exec -i swiftpay-postgres-ledger psql -U swiftpay -d swiftpay_ledger -q -v ON_ERROR_STOP=1 < load-test/seed-accounts.sql >/dev/null; }
wipe() { pgc "TRUNCATE payments" >/dev/null; plc "TRUNCATE transactions" >/dev/null; pac "TRUNCATE analytics.payment_events" >/dev/null; }

echo "==> health"
for n in swiftpay-gateway swiftpay-ledger swiftpay-analytics swiftpay-kafka swiftpay-redis; do
  [ "$(docker inspect -f '{{.State.Health.Status}}' "$n" 2>/dev/null)" = healthy ] || { echo "ABORT: $n not healthy"; exit 1; }
done

echo "==> seed + warm-up (${WARMUP_S}s @ ${RATE})"
seed
k6 run -e DURATION_S="$WARMUP_S" -e RATE="$RATE" --quiet load-test/swiftpay-load.js >/dev/null 2>&1 || true

echo "==> clean baseline"
wipe; seed
echo "    gw=$(pgc 'SELECT COUNT(*) FROM payments') led=$(plc 'SELECT COUNT(*) FROM transactions') an=$(pac 'SELECT COUNT(*) FROM analytics.payment_events')"
plc "SELECT '    ledger balance total = '||SUM(balance) FROM accounts WHERE user_id LIKE 'load-user-%'"

echo "==> start capture"
load-test/_capture-start.sh

echo "==> k6: ${RATE} req/s x ${DURATION_S}s = $((RATE*DURATION_S)) requests"
date '+%Y-%m-%dT%H:%M:%S%z' > "$ART/run-start.txt"
SAMPLE_S=120 nohup load-test/_monitor.sh > "$ART/monitor-run.log" 2>&1 &
k6 run --summary-export="$ART/k6-summary.json" \
  -e GATEWAY_URL=http://localhost:8080 -e RATE="$RATE" -e DURATION_S="$DURATION_S" \
  load-test/swiftpay-load.js 2>&1 | tee "$ART/k6-stdout.log"
date '+%Y-%m-%dT%H:%M:%S%z' > "$ART/run-end.txt"

echo "==> drain + stop capture"
sleep 75
load-test/_capture-stop.sh

echo "==> reconciliation"
{
  echo "gateway.payments         : $(pgc 'SELECT COUNT(*) FROM payments')"
  echo "ledger.transactions      : $(plc 'SELECT COUNT(*) FROM transactions')"
  plc "SELECT '  '||status||' : '||COUNT(*) FROM transactions GROUP BY status ORDER BY status"
  echo "analytics.payment_events : $(pac 'SELECT COUNT(*) FROM analytics.payment_events')"
  echo "ledger balance total     : $(plc "SELECT SUM(balance) FROM accounts WHERE user_id LIKE 'load-user-%'")  (expect 1000000000000.0000, unchanged)"
} | tee "$ART/reconciliation.txt"
