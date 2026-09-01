<!-- Filled in after the run completes. Placeholders: {{...}} -->
# SwiftPay Load Test Report

**Spec requirement (§5):** *Perform a load test at 250 TPS for a total of 1 million
transactions, and provide the resulting PCAP trace.*

## 1. Setup

| | |
|---|---|
| Code under test | commit `{{COMMIT}}` (branch `main`) |
| Deployment | `docker compose up --build` — 3 services + 3 Postgres + Kafka + ZooKeeper + Redis, all `(healthy)` |
| Host | Apple Silicon, 10 vCPU, 16 GB RAM; Docker via colima (`{{COLIMA}}`) |
| Load generator | k6 `{{K6VER}}`, `constant-arrival-rate` executor |
| Offered load | **250 req/s for 4,000 s = 1,000,000 `POST /v1/payments`** |
| Account pool | 10,000 seeded accounts (`load-user-*`), 100,000,000 balance each |
| Workload | unique `transactionId` per request; random distinct sender/receiver; amount 1.00–4.99 USD |
| Window | `{{START}}` → `{{END}}` |

## 2. Result summary

| Metric | Value |
|---|---|
| Requests sent | {{HTTP_REQS}} |
| Dropped / interrupted iterations | {{DROPPED}} |
| HTTP failures (non-2xx / errors) | {{HTTP_FAILED}} |
| Achieved throughput | {{RPS}} req/s |
| Latency avg / p90 / p95 / p99 / max | {{LAT}} |
| gateway `payments` rows created | {{GW_ROWS}} |
| ledger `transactions` — COMPLETED / FAILED | {{LED_COMPLETED}} / {{LED_FAILED}} |
| analytics `payment_events` rows | {{AN_ROWS}} |
| Ledger money conservation | {{BALANCE}} (expected 1,000,000,000,000.0000, unchanged) |

**End-to-end reconciliation:** {{RECON_VERDICT}}

## 3. Stability over the run

Sampled every 120 s (`artifacts/timeseries.csv`):

{{TIMESERIES_NOTE}}

## 4. PCAP trace

| | |
|---|---|
| Capture point | Docker bridge `{{BRIDGE}}` inside the colima VM |
| Snap length | 160 bytes (headers + protocol-identifying payload) |
| Duration | full run |
| Packets | {{PKTS}} |
| Size | {{PCAP_RAW}} raw / {{PCAP_GZ}} gzip |
| Location | GitHub Release `{{RELEASE_TAG}}` → `{{PCAP_ASSET}}` |
| SHA-256 | `{{PCAP_SHA}}` |

### Traffic composition (by TCP port)

| Component | Port | Packets | Share |
|---|---|---|---|
| Gateway API (HTTP) | 8080 | {{P8080}} | {{S8080}} |
| Kafka | 29092 | {{P29092}} | {{S29092}} |
| PostgreSQL (×3) | 5432 | {{P5432}} | {{S5432}} |
| Redis | 6379 | {{P6379}} | {{S6379}} |
| ZooKeeper | 2181 | {{P2181}} | {{S2181}} |

### Health signals in the capture

| Check | Finding |
|---|---|
| TCP retransmissions | {{RETRANS}} |
| TCP RST | {{RSTS}} |
| Kafka retry/DLT topic traffic (`*-retry`, `*-dlt`) | {{KAFKA_RETRY}} |
| `payment.failed` events | {{PAY_FAILED}} |

### How to verify

```bash
gunzip -k swiftpay-loadtest.pcap.gz
capinfos swiftpay-loadtest.pcap
# API calls:
tshark -r swiftpay-loadtest.pcap -Y 'http.request.method == "POST"' | head
# Kafka produce/fetch:
tshark -r swiftpay-loadtest.pcap -d tcp.port==29092,kafka -Y kafka | head
# Postgres + Redis:
tshark -r swiftpay-loadtest.pcap -d tcp.port==5432,pgsql -Y pgsql | head
tshark -r swiftpay-loadtest.pcap -d tcp.port==6379,redis -Y redis | head
```

## 5. Conclusion

{{CONCLUSION}}
