# SwiftPay Load Test — 250 TPS × 1,000,000 transactions

This directory contains the load-test harness required by the project spec
(§5: *"Perform a load test at 250 TPS for a total of 1 million transactions,
and provide the resulting PCAP trace"*) and the artifacts from an execution.

## What it does

`k6` drives **250 requests/second for 4,000 seconds → 1,000,000 `POST /v1/payments`**
against the gateway. Every request is a unique P2P payment between two randomly
chosen accounts from a pool of 10,000 seeded, well-funded accounts, so the run
exercises the **happy path** end-to-end:

```
k6 ──HTTP──► gateway ──Redis SETNX (idempotency)
                     ──INSERT payments (PENDING) ──► postgres-gateway
                     ──produce payment.initiated ──► kafka
kafka ──► ledger  ──SELECT … FOR UPDATE / debit / credit ──► postgres-ledger
                  ──produce payment.completed ──► kafka
kafka ──► analytics ──INSERT payment_events ──► postgres-analytics
```

## Files

| File | Purpose |
|---|---|
| `swiftpay-load.js` | k6 scenario (`constant-arrival-rate`, 250 rps, 4000 s) |
| `seed-accounts.sql` | seeds `load-user-000001..010000`, balance 100,000,000 each |
| `run-loadtest.sh` | full orchestrator: health → seed → warm → baseline → capture → k6 → reconcile |
| `_capture-start.sh` / `_capture-stop.sh` | packet capture on the Docker bridge inside the colima VM |
| `_monitor.sh` | samples DB counts / pcap size every 120 s → `artifacts/timeseries.csv` |
| `artifacts/` | k6 summary, time series, reconciliation, logs, and the PCAP index |

## Packet capture

SwiftPay's inter-service traffic (Kafka, PostgreSQL, Redis) happens **inside the
Docker network**, which on this machine lives in the colima VM — it never reaches
the macOS host loopback. The capture therefore runs **inside the VM on the Docker
bridge interface** (`br-<netid>`), where it sees:

* inbound gateway API calls (`:8080`, after DNAT)
* Kafka (`:29092`) — produce/fetch for `payment.initiated` / `payment.completed`
* PostgreSQL (`:5432`) — all three service databases
* Redis (`:6379`) — idempotency `SET NX`
* ZooKeeper (`:2181`) — Kafka coordination

Snap length is 160 bytes: full Ethernet/IP/TCP headers plus enough payload to
identify every protocol (HTTP request/status lines, Kafka API keys, PostgreSQL
message types, Redis commands) while keeping the artifact to a single uploadable
file.

The full-run PCAP (multi-GB) is attached to a **GitHub Release**, not committed
to the tree — see `artifacts/PCAP.md` for the link and verification steps.

## Reproducing

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
docker compose up --build -d          # from repo root
./load-test/run-loadtest.sh           # ~5 min warm-up + ~67 min run
```
