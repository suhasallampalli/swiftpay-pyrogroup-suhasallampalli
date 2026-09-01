# PCAP traces — 250 TPS / 1,000,000-transaction load test

Both captures were taken with `tcpdump` on the Docker bridge **inside the colima
VM** (`br-e5ba621b15b2`), the only vantage point where every SwiftPay traffic
type is visible together: gateway API (`:8080`), Kafka (`:29092`), PostgreSQL
(`:5432`), Redis (`:6379`), ZooKeeper (`:2181`).

| Capture | Scope | Snap | Packets | Size (raw / gz) | Location |
|---|---|---|---|---|---|
| `swiftpay-window.pcap.gz` | 90 s steady-state slice, ~24,000 transactions | full | 1,185,486 | 221 MB / **40 MB** | **this repo**, next to this file |
| `swiftpay-loadtest.pcap.gz` | entire run — 1,000,000 transactions, 68 min | 160 B | 49,621,721 | 6.4 GB / **1.5 GB** | **GitHub Release [`loadtest-1M-20260901`](https://github.com/suhasallampalli/swiftpay-pyrogroup-suhasallampalli/releases/tag/loadtest-1M-20260901)** |

Neither capture dropped a packet at the kernel (`0 packets dropped by kernel`).

## SHA-256

```
a8b6b9a3f9bc2b4b71742831171d3c987b2c72b5002848aa47752f2e8887236a  swiftpay-window.pcap.gz
9e0c807eaa34f37abe8a04143ead4bc5b4cf7829a9386bffd85586e237328fb6  swiftpay-loadtest.pcap.gz
```

## What the capture shows

* **API** — `POST /v1/payments HTTP/1.1` → `HTTP/1.1 202`, ~250/s, continuously
* **Redis** — `SET "idempotency:txn:load-…" "PROCESSING" "EX" "86400" "NX"` per request
* **PostgreSQL** — per-payment `BEGIN … COMMIT`; the ledger's `SELECT … FOR UPDATE`
  + balance `UPDATE`s; the analytics `INSERT`
* **Kafka** — produce/fetch on `payment.initiated` and `payment.completed`
* **Health** — 0 TCP RST across 49.6 M packets; ~2.5 new connections/s (stable
  pools); Kafka `*-retry-*` and `*-dlt` topics carry **zero** messages

## Verify

```bash
gunzip -k swiftpay-window.pcap.gz
capinfos swiftpay-window.pcap

tshark -r swiftpay-window.pcap -q -z conv,tcp | grep -E ':8080|:29092|:5432|:6379'
tshark -r swiftpay-window.pcap -Y 'http.request.method == "POST"' -T fields \
       -e frame.time_relative -e ip.src -e ip.dst -e http.request.uri | head
tshark -r swiftpay-window.pcap -d tcp.port==29092,kafka -Y kafka | head
tshark -r swiftpay-window.pcap -d tcp.port==5432,pgsql  -Y pgsql | head
tshark -r swiftpay-window.pcap -d tcp.port==6379,redis  -Y redis | head

# full run, from the Release:
shasum -a 256 swiftpay-loadtest.pcap.gz     # compare to hash above
gunzip swiftpay-loadtest.pcap.gz
capinfos swiftpay-loadtest.pcap             # ~49.6M pkts, ~68 min
```
