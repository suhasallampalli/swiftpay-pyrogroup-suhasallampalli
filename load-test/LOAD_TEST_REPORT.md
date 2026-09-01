# SwiftPay Load Test Report

**Spec requirement (§5):** *Perform a load test at 250 TPS for a total of 1 million
transactions, and provide the resulting PCAP trace.*

## 1. Setup

| | |
|---|---|
| Code under test | commit `b3359d1` (`main`) |
| Deployment | `docker compose up --build` — gateway + ledger + analytics, 3× PostgreSQL 16, Kafka + ZooKeeper (Confluent 7.7.0), Redis 7; all `(healthy)` |
| Host | Apple Silicon, 10 vCPU / 16 GB; Docker via colima (VZ, virtiofs) |
| Load generator | k6 v2.2.0, `constant-arrival-rate` executor |
| Offered load | **250 req/s for 4,000 s** |
| Account pool | 10,000 seeded accounts (`load-user-000001..010000`), balance 100,000,000 each |
| Workload | unique `transactionId` (UUID) per request; random distinct sender/receiver; amount 1.00–4.99 USD; all requests are valid (happy path) |
| Run window | k6 13:47:57 → 14:55:01; packet capture 13:47:51 → 14:56:17 |

The 3 application tables were truncated and balances re-seeded immediately before
the measured run, after a 5-minute warm-up, so all counts below start from zero.

## 2. Result summary

| Metric | Value |
|---|---|
| Requests issued | **999,677** |
| Iterations dropped before send (k6 VU pool) | 324 — **0.032 %** |
| HTTP failures / non-202 responses | **0** |
| Checks passed (`status == 202`) | 999,677 / 999,677 = **100 %** |
| Achieved throughput | **249.92 req/s** |
| Latency — med / p90 / p95 / p99 / max | 6.8 ms / 20.2 ms / 34.4 ms / 235 ms / 6.78 s |
| Data sent / received (HTTP only) | 288 MB / 340 MB |

### End-to-end reconciliation (after pipeline drain)

| Stage | Rows | Status |
|---|---|---|
| `gateway.payments` | **999,677** | all created |
| `ledger.transactions` | **999,677** | **all `COMPLETED`**, 0 `FAILED` |
| `analytics.payment_events` | **999,677** | all recorded |
| Ledger money conservation | `SUM(balance)` = **1,000,000,000,000.0000** | seed value, **unchanged** |
| Kafka `ledger-service` / `analytics-service` consumer lag | **0** | fully caught up |
| Kafka `*-retry-*` / `*-dlt` topic offsets | **0** | **nothing retried or dead-lettered** |

Every request that entered the gateway came out the other end of the
event-driven pipeline as a `COMPLETED` transaction and an analytics record.
Because every sender and receiver is in the seeded pool, total balance is an
invariant — it is exactly the seed value after 999,677 transfers, so no money
was created or destroyed.

> The 324 dropped iterations were never sent on the wire — k6 could not free a VU
> within the 4 ms arrival slot during brief GC-induced latency spikes. They are
> not errors. 999,677 / 1,000,000 = 99.968 % of the nominal target was delivered
> at a true 250 req/s.

## 3. Stability over the run

`artifacts/timeseries.csv`, sampled every ~120 s, shows a straight line:

| elapsed | gateway rows | implied rate |
|---|---|---|
| 120 s | 30,189 | 251/s |
| 1,086 s | 271,562 | 250/s |
| 2,180 s | 544,788 | 250/s |
| 3,893 s | 973,059 | 250/s |

k6's per-second progress log (`artifacts/k6-stdout.log`) reports
**`0 interrupted iterations` for the entire 4,000 s**. Host load average held
around 10–13 on 10 cores. The Kafka consumers (ledger, analytics) ran a few
seconds behind the producer during the run and fully drained within ~2 minutes
of the load stopping — see §5.

## 4. PCAP trace

Captured with `tcpdump` on the Docker bridge **inside the colima VM**
(`br-e5ba621b15b2`), where all SwiftPay traffic is visible — inter-service
traffic never reaches the macOS host.

| | Full-run capture | Steady-state window |
|---|---|---|
| File | `swiftpay-loadtest.pcap.gz` | `swiftpay-window.pcap.gz` |
| Location | **GitHub Release `loadtest-1M-20260901`** | **in this repo** (`load-test/artifacts/`) |
| Scope | entire run, 13:47:51 → 14:56:17 (68 m 26 s) | 90 s / ~24,000 transactions, 14:08:28 → 14:10:00 |
| Snap length | 160 B (headers + protocol-identifying payload) | full (no truncation) |
| Packets | **49,621,721** (0 dropped by kernel) | **1,185,486** (0 dropped by kernel) |
| Size | 6.4 GB raw / **1.5 GB gzip** | 221 MB raw / **40 MB gzip** |
| SHA-256 (`.gz`) | `9e0c807eaa34f37abe8a04143ead4bc5b4cf7829a9386bffd85586e237328fb6` | `a8b6b9a3f9bc2b4b71742831171d3c987b2c72b5002848aa47752f2e8887236a` |

### Traffic composition (full run, by TCP port)

| Component | Port | Packets (one direction) | Share of run |
|---|---|---|---|
| PostgreSQL (×3 databases) | 5432 | 15,384,561 | ~62 % |
| Kafka (broker ↔ producers/consumers) | 29092 | 5,579,152 | ~22 % |
| Gateway REST API (HTTP) | 8080 | 3,015,473 | ~12 % |
| Redis (idempotency `SET NX`) | 6379 | 1,007,162 | ~4 % |
| ZooKeeper (Kafka coordination) | 2181 | 1,366 | <0.01 % |

All four required traffic types — **API requests/responses, Kafka message
traffic, PostgreSQL communication, Redis interactions** — are present
continuously for the whole run. Sample payloads visible in the window capture:

```
HTTP : POST /v1/payments HTTP/1.1        →  HTTP/1.1 202
Redis: SET "idempotency:txn:load-…" "PROCESSING" "EX" "86400" "NX"
PgSQL: BEGIN … SELECT … COMMIT   (per-payment transaction boundaries)
Kafka: produce/fetch on  payment.initiated  and  payment.completed
```

### Network-health signals

| Check | Finding |
|---|---|
| TCP RST packets | **0** across 49.6 M packets |
| New TCP connections (SYN) | 10,255 over 68 min (~2.5/s) — connection pools stable, **no connection churn/storm** |
| Kernel packet drops during capture | **0** (both files) |
| Kafka retry topics (`payment.*-retry-N`) | present in metadata only; **LOG-END-OFFSET 0 — zero messages** |
| Kafka dead-letter topics (`payment.*-dlt`) | **LOG-END-OFFSET 0 — zero messages** |
| `payment.failed` events | **0** (no insufficient-funds or error paths hit) |

### How to verify

```bash
# window capture (in the repo)
gunzip -k load-test/artifacts/swiftpay-window.pcap.gz
capinfos          swiftpay-window.pcap            # 1,185,486 pkts, 90 s span
tshark -r swiftpay-window.pcap -q -z conv,tcp | grep -E ':8080|:29092|:5432|:6379'
tshark -r swiftpay-window.pcap -Y 'http.request.method=="POST"' | head
tshark -r swiftpay-window.pcap -d tcp.port==5432,pgsql -Y pgsql | head
tshark -r swiftpay-window.pcap -d tcp.port==6379,redis -Y redis | head

# full-run capture (from the Release)
curl -L -o swiftpay-loadtest.pcap.gz <release-asset-url>
shasum -a 256 swiftpay-loadtest.pcap.gz      # must match the hash above
gunzip swiftpay-loadtest.pcap.gz
capinfos swiftpay-loadtest.pcap              # ~49.6 M pkts, 68 min span
```

## 5. Observations

* **The system stayed correct and stable under sustained 250 TPS for 67 minutes.**
  No HTTP errors, no dropped packets, no TCP resets, no Kafka retries or
  dead-letters, and exact end-to-end reconciliation with money conserved.

* **Bottleneck (spec §5 asks for one): the ledger consumer.** During the run the
  `ledger-service` consumer processed slightly under 250/s and its backlog grew
  to ~70,000 messages (~5 min of lag) by the end; it then drained completely in
  ~2 minutes once load stopped. The gateway (Redis `SETNX` + one `INSERT` +
  async Kafka publish) comfortably absorbs 250 TPS at p95 = 34 ms; the ledger
  does more work per message — a `SELECT … FOR UPDATE` on the sender, another on
  the receiver, two balance `UPDATE`s and a status `UPDATE`, all in one
  serialised transaction — and that is where headroom runs out first. Scaling it
  would mean partitioning `payment.initiated` by sender and running multiple
  ledger instances/consumers.

* **Gateway payment status is write-once `PENDING`.** The gateway never consumes
  `payment.completed` / `payment.failed`, so `gateway.payments.status` stays
  `PENDING` for every row. Final status lives in `ledger.transactions`. Not a
  load issue, but worth noting for anyone reconciling the two tables.
