<!-- values filled in after the run -->
# PCAP traces — 250 TPS / 1,000,000-transaction load test

Two captures, both taken on the Docker bridge inside the colima VM
(`br-{{NETID}}`), where every SwiftPay traffic type is visible:
gateway API (`:8080`), Kafka (`:29092`), PostgreSQL (`:5432`), Redis (`:6379`),
ZooKeeper (`:2181`).

| Capture | Scope | Snap | Packets | Size | Location |
|---|---|---|---|---|---|
| `swiftpay-window.pcap.gz` | ~90 s steady-state slice (~{{WINDOW_TXN}} transactions) | full (262144) | {{WINDOW_PKTS}} | {{WINDOW_SIZE}} | **in this repo** (`load-test/artifacts/`) |
| `swiftpay-loadtest.pcap.gz` | entire run (1,000,000 transactions, ~67 min) | 160 B | {{FULL_PKTS}} | {{FULL_SIZE}} | **GitHub Release [`{{TAG}}`]({{RELEASE_URL}})** |

## SHA-256

```
{{WINDOW_SHA}}  swiftpay-window.pcap.gz
{{FULL_SHA}}  swiftpay-loadtest.pcap.gz
```

## Verify

```bash
# in-repo window
gunzip -k load-test/artifacts/swiftpay-window.pcap.gz
capinfos load-test/artifacts/swiftpay-window.pcap

# component traffic is all present:
tshark -r swiftpay-window.pcap -q -z conv,tcp | sort -k1 | grep -E ':8080|:29092|:5432|:6379'

# HTTP API requests/responses with bodies:
tshark -r swiftpay-window.pcap -Y 'http' -T fields -e ip.src -e ip.dst -e http.request.method -e http.request.uri -e http.response.code | head -20

# Kafka produce (payment.initiated) and fetch (ledger/analytics consumers):
tshark -r swiftpay-window.pcap -d tcp.port==29092,kafka -Y 'kafka.request_key == 0 || kafka.request_key == 1' | head

# PostgreSQL and Redis:
tshark -r swiftpay-window.pcap -d tcp.port==5432,pgsql -Y pgsql | head
tshark -r swiftpay-window.pcap -d tcp.port==6379,redis -Y redis | head

# full-run capture (from the Release):
gzip -d swiftpay-loadtest.pcap.gz
capinfos swiftpay-loadtest.pcap    # confirms ~67 min span, packet count
```
