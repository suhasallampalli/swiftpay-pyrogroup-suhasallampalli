#!/usr/bin/env bash
# Stop the SwiftPay load-test packet capture and copy the pcap out of the VM,
# gzipped, into load-test/artifacts/.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ART="$REPO_ROOT/load-test/artifacts"
PCAP_VM="${PCAP_VM:-/mnt/lima-colima/swiftpay-loadtest.pcap}"
mkdir -p "$ART"

colima ssh -- sudo sh -c "kill -INT \$(cat /tmp/loadcap.pid) 2>/dev/null || true; sleep 3"
colima ssh -- sudo bash -c "ls -lh '$PCAP_VM'; command -v capinfos >/dev/null && capinfos '$PCAP_VM' || true"

echo "gzip + copy out (this can take a few minutes for a multi-GB capture)..."
colima ssh -- sudo sh -c "gzip -1 -c '$PCAP_VM'" > "$ART/swiftpay-loadtest.pcap.gz"
ls -lh "$ART/swiftpay-loadtest.pcap.gz"
echo "done -> $ART/swiftpay-loadtest.pcap.gz"
