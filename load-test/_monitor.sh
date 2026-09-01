#!/usr/bin/env bash
# Samples the SwiftPay stack every SAMPLE_S seconds while the k6 run is alive,
# writing a CSV time series. When k6 exits, captures post-run totals and stops.
set -u
export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"

ART="$(cd "$(dirname "$0")/artifacts" && pwd)"
SAMPLE_S="${SAMPLE_S:-120}"
K6_PID="$(cat "$ART/k6.pid")"
CSV="$ART/timeseries.csv"

pg() { docker exec swiftpay-postgres-gateway   psql -U swiftpay -d swiftpay_gateway   -tAc "$1" 2>/dev/null | tr -d ' '; }
pl() { docker exec swiftpay-postgres-ledger    psql -U swiftpay -d swiftpay_ledger    -tAc "$1" 2>/dev/null | tr -d ' '; }
pa() { docker exec swiftpay-postgres-analytics psql -U swiftpay -d swiftpay_analytics -tAc "$1" 2>/dev/null | tr -d ' '; }

echo "ts,elapsed_s,gateway_payments,ledger_txns,ledger_completed,ledger_failed,analytics_events,pcap_bytes,load_1min" > "$CSV"
START=$(date +%s)

while kill -0 "$K6_PID" 2>/dev/null; do
  NOW=$(date +%s); EL=$((NOW-START))
  GP=$(pg "SELECT COUNT(*) FROM payments")
  LT=$(pl "SELECT COUNT(*) FROM transactions")
  LC=$(pl "SELECT COUNT(*) FROM transactions WHERE status='COMPLETED'")
  LF=$(pl "SELECT COUNT(*) FROM transactions WHERE status='FAILED'")
  AE=$(pa "SELECT COUNT(*) FROM analytics.payment_events")
  PB=$(stat -f%z "$ART/swiftpay-loadtest.pcap" 2>/dev/null || echo 0)
  LD=$(uptime | sed -E 's/.*load averages?: ([0-9.]+).*/\1/')
  echo "$(date '+%H:%M:%S'),$EL,$GP,$LT,$LC,$LF,$AE,$PB,$LD" >> "$CSV"
  sleep "$SAMPLE_S"
done

echo "k6 exited at $(date '+%H:%M:%S'); draining 60s before post-run snapshot" >> "$ART/monitor.log"
sleep 60
{
  echo "== post-run =="
  echo "gateway.payments        : $(pg "SELECT COUNT(*) FROM payments")"
  echo "ledger.transactions     : $(pl "SELECT COUNT(*) FROM transactions")"
  pl "SELECT status||' : '||COUNT(*) FROM transactions GROUP BY status ORDER BY status"
  echo "analytics.payment_events: $(pa "SELECT COUNT(*) FROM analytics.payment_events")"
  echo "distinct load txns (gw) : $(pg "SELECT COUNT(*) FROM payments WHERE transaction_id LIKE 'load-%'")"
  echo "ledger sum(balance)     : $(pl "SELECT SUM(balance) FROM accounts WHERE user_id LIKE 'load-user-%'")"
} > "$ART/postrun.txt" 2>&1
cat "$ART/postrun.txt"
