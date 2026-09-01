#!/usr/bin/env bash
# Publish the full-run PCAP as a GitHub Release asset.
# Requires: gh authenticated (`gh auth login`) and
#           load-test/artifacts/swiftpay-loadtest.pcap.gz present.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$REPO_ROOT"

REPO="suhasallampalli/swiftpay-pyrogroup-suhasallampalli"
TAG="loadtest-1M-20260901"
PCAP="load-test/artifacts/swiftpay-loadtest.pcap.gz"
SHA="9e0c807eaa34f37abe8a04143ead4bc5b4cf7829a9386bffd85586e237328fb6"

[ -f "$PCAP" ] || { echo "missing $PCAP"; exit 1; }
echo "verifying checksum..."
[ "$(shasum -a 256 "$PCAP" | awk '{print $1}')" = "$SHA" ] || { echo "SHA-256 mismatch"; exit 1; }

NOTES="$(mktemp)"
cat > "$NOTES" <<'EOF'
Full packet capture from the SwiftPay load test: **250 req/s x 4,000 s -> 999,677 POST /v1/payments**.

| | |
|---|---|
| Captured on | Docker bridge `br-e5ba621b15b2` inside the colima VM (all inter-service traffic) |
| Span | 2026-09-01 13:47:51 -> 14:56:17 (68 min 26 s) |
| Packets | 49,621,721 - 0 dropped by kernel |
| Snap length | 160 B (full Ethernet/IP/TCP headers + protocol-identifying payload) |
| Size | 6.4 GB raw / 1.5 GB gzip |
| SHA-256 (.gz) | `9e0c807eaa34f37abe8a04143ead4bc5b4cf7829a9386bffd85586e237328fb6` |

Traffic: PostgreSQL :5432 ~62%, Kafka :29092 ~22%, gateway API :8080 ~12%, Redis :6379 ~4%, ZooKeeper :2181 <0.01%.
Health: 0 TCP RST across 49.6M packets, ~2.5 new connections/s, Kafka `*-retry-*` / `*-dlt` topics carry zero messages.

A 90 s full-detail window (`swiftpay-window.pcap.gz`, 40 MB) is committed in the repo at `load-test/artifacts/`.
Full analysis: `load-test/LOAD_TEST_REPORT.md`.

```
gzip -d swiftpay-loadtest.pcap.gz
capinfos swiftpay-loadtest.pcap
```
EOF

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "release $TAG exists — uploading asset"
  gh release upload "$TAG" "$PCAP" --repo "$REPO" --clobber
else
  gh release create "$TAG" "$PCAP" \
    --repo "$REPO" \
    --title "Load test - 1M transactions @ 250 TPS (full PCAP)" \
    --notes-file "$NOTES"
fi
rm -f "$NOTES"
echo "done: https://github.com/$REPO/releases/tag/$TAG"
