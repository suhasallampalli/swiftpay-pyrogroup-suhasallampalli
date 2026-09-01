# SwiftPay — Event-Driven Payment Processing Platform

SwiftPay is a production-grade, event-driven payment processing system built with Java 21 and Spring Boot 3.3.5. It follows a microservices architecture where three independently deployable services communicate asynchronously through Apache Kafka.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Services](#services)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Running Locally](#running-locally)
- [API Reference](#api-reference)
- [Event Flow](#event-flow)
- [Configuration](#configuration)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Design Decisions](#design-decisions)

---

## Architecture Overview

```
┌─────────────┐     POST /v1/payments      ┌──────────────────────┐
│   Client    │ ─────────────────────────► │  Service A: Gateway  │
│             │ ◄─────────────────────────  │  (REST API · :8080)  │
└─────────────┘     202 Accepted (PENDING)  └──────────┬───────────┘
                                                        │
                                            Kafka: payment.initiated
                                                        │
                                            ┌───────────▼───────────┐
                                            │  Service B: Ledger    │
                                            │  (Processor · :8081)  │
                                            └───────────┬───────────┘
                                                        │
                                         Kafka: payment.completed
                                              payment.failed
                                                        │
                                            ┌───────────▼───────────┐
                                            │ Service C: Analytics  │
                                            │  (Worker · :8082)     │
                                            └───────────────────────┘
```

Each service owns its own PostgreSQL database and communicates exclusively through Kafka — no direct service-to-service HTTP calls.

---

## Services

### Service A — Gateway (`:8080`)
The public-facing REST API. Accepts payment requests, enforces idempotency via Redis, persists a `PENDING` payment record, and publishes a `PaymentInitiatedEvent` to Kafka.

**Key responsibilities:**
- Input validation (`@Valid` on all request fields)
- Idempotency: uses `Redis SETNX` with 24-hour TTL so duplicate requests with the same `transactionId` are safely deduplicated
- Publishes events to Kafka without waiting for processing outcome (async, non-blocking)
- Swagger UI available at `/swagger-ui.html`

### Service B — Ledger (`:8081`)
An internal Kafka consumer that processes payment events. It performs the actual financial transaction atomically: debiting the sender and crediting the receiver within a single database transaction using pessimistic locking.

**Key responsibilities:**
- Consumes `payment.initiated` events from Kafka
- Acquires a pessimistic write lock on the sender's account row before any balance check
- Deducts sender balance, credits receiver balance — all in one `@Transactional` method
- On insufficient funds: marks transaction `FAILED`, publishes `PaymentFailedEvent`
- On success: marks transaction `COMPLETED`, publishes `PaymentCompletedEvent`
- Retry handling via `@RetryableTopic` (4 attempts, exponential backoff up to 10 s)
- Exposes transaction history via `GET /v1/users/{userId}/transactions`

### Service C — Analytics (`:8082`)
A lightweight Kafka consumer that records every completed payment into an analytics database for reporting and auditing.

**Key responsibilities:**
- Consumes `payment.completed` events from Kafka
- Stores events in a dedicated `analytics` PostgreSQL schema
- Deduplicates events by `transactionId` (idempotent writes)
- Exposes summary statistics via `GET /v1/analytics/summary`
- Exposes recent event list via `GET /v1/analytics/events`

---

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Build tool | Maven 3.9 (wrapper included) |
| Messaging | Apache Kafka (Confluent 7.7.0) |
| Databases | PostgreSQL 16 (one per service) |
| Cache / Idempotency | Redis 7 |
| Schema migrations | Flyway |
| ORM | Spring Data JPA / Hibernate 6.5 |
| API docs | springdoc-openapi 2.6.0 (Swagger UI) |
| Observability | Spring Boot Actuator |
| Containerisation | Docker + Docker Compose |
| Unit testing | JUnit 5, Mockito |
| Integration testing | H2 (PostgreSQL mode) + Spring EmbeddedKafka |
| CI | GitHub Actions |

---

## Project Structure

```
swiftpay-pyrogroup/
├── pom.xml                          # Parent POM (multi-module)
├── mvnw / mvnw.cmd                  # Maven wrapper
├── docker-compose.yml               # Full-stack orchestration
├── .github/
│   └── workflows/ci.yml             # GitHub Actions pipeline
├── docs/
│   ├── PROJECT_SPEC.md
│   └── Claude.md
│
├── common-events/                   # Shared Kafka event POJOs
│   └── src/main/java/com/swiftpay/events/
│       ├── PaymentInitiatedEvent.java
│       ├── PaymentCompletedEvent.java
│       └── PaymentFailedEvent.java
│
├── service-a-gateway/
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/swiftpay/gateway/
│       │   ├── config/              # RedisConfig, OpenApiConfig
│       │   ├── controller/          # PaymentController
│       │   ├── dto/                 # PaymentRequest, PaymentResponse, ErrorResponse
│       │   ├── exception/           # GlobalExceptionHandler
│       │   ├── kafka/               # PaymentEventProducer
│       │   ├── model/               # Payment, PaymentStatus
│       │   ├── repository/          # PaymentRepository
│       │   └── service/             # PaymentService
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__create_payments.sql
│       └── test/                    # Unit + integration tests
│
├── service-b-ledger/
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/swiftpay/ledger/
│       │   ├── config/              # KafkaConfig
│       │   ├── controller/          # LedgerController
│       │   ├── dto/                 # TransactionHistoryResponse
│       │   ├── exception/           # GlobalExceptionHandler
│       │   ├── kafka/               # PaymentInitiatedConsumer, PaymentEventProducer
│       │   ├── model/               # Account, Transaction, TransactionStatus
│       │   ├── repository/          # AccountRepository, TransactionRepository
│       │   └── service/             # LedgerService
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__create_ledger_schema.sql
│       └── test/                    # Unit + integration tests
│
└── service-c-analytics/
    ├── Dockerfile
    └── src/
        ├── main/java/com/swiftpay/analytics/
        │   ├── config/              # KafkaConfig
        │   ├── controller/          # AnalyticsController
        │   ├── kafka/               # PaymentCompletedConsumer
        │   ├── model/               # AnalyticsRecord
        │   ├── repository/          # AnalyticsRepository
        │   └── service/             # AnalyticsService
        ├── main/resources/
        │   ├── application.yml
        │   └── db/migration/V1__create_analytics_schema.sql
        └── test/                    # Unit + integration tests
```

---

## Getting Started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Temurin or OpenJDK |
| Docker | 24+ | With Compose v2 plugin |
| Maven | 3.9+ | Or use the included `mvnw` wrapper |

### Running with Docker Compose

This is the recommended way to run the full stack.

```bash
# 1. Clone the repository
git clone https://github.com/suhasallampalli/swiftpay-pyrogroup-suhasallampalli.git
cd swiftpay-pyrogroup-suhasallampalli

# 2. Build all JARs (skip tests for speed)
./mvnw clean package -DskipTests

# 3. Start the full stack (infra + all three services)
docker compose up --build -d

# 4. Verify all containers are healthy
docker compose ps
```

All 9 containers should show `(healthy)` within ~60 seconds:

| Container | Port | Role |
|---|---|---|
| swiftpay-gateway | 8080 | Service A — REST API |
| swiftpay-ledger | 8081 | Service B — Ledger processor |
| swiftpay-analytics | 8082 | Service C — Analytics worker |
| swiftpay-postgres-gateway | 5432 | PostgreSQL for Gateway |
| swiftpay-postgres-ledger | 5433 | PostgreSQL for Ledger |
| swiftpay-postgres-analytics | 5434 | PostgreSQL for Analytics |
| swiftpay-kafka | 9092 | Apache Kafka broker |
| swiftpay-zookeeper | 2181 | Kafka coordination |
| swiftpay-redis | 6379 | Idempotency cache |

**Tear down:**
```bash
docker compose down -v   # -v also removes database volumes
```

### Running Locally

To run services locally against infrastructure running in Docker:

```bash
# Start only infrastructure
docker compose up -d postgres-gateway postgres-ledger postgres-analytics kafka zookeeper redis

# Run Gateway (terminal 1)
cd service-a-gateway
../mvnw spring-boot:run

# Run Ledger (terminal 2)
cd service-b-ledger
../mvnw spring-boot:run

# Run Analytics (terminal 3)
cd service-c-analytics
../mvnw spring-boot:run
```

---

## API Reference

### Service A — Gateway (`localhost:8080`)

Swagger UI: http://localhost:8080/swagger-ui.html

#### `POST /v1/payments` — Initiate a payment

```bash
curl -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-abc-123",
    "senderId":      "user-001",
    "receiverId":    "user-002",
    "amount":        150.00,
    "currency":      "USD"
  }'
```

**Response `202 Accepted`:**
```json
{
  "transactionId": "txn-abc-123",
  "senderId":      "user-001",
  "receiverId":    "user-002",
  "amount":        150.00,
  "currency":      "USD",
  "status":        "PENDING",
  "createdAt":     "2026-08-31T13:00:00Z"
}
```

| Status | Meaning |
|---|---|
| `202 Accepted` | Payment accepted, processing asynchronously |
| `400 Bad Request` | Validation failure (missing field, negative amount, etc.) |

#### `GET /v1/payments/{transactionId}` — Get payment status

```bash
curl http://localhost:8080/v1/payments/txn-abc-123
```

**Response `200 OK`:** Same shape as above with current `status`.

| Status | Meaning |
|---|---|
| `200 OK` | Payment found |
| `404 Not Found` | No payment with this transactionId |

---

### Service B — Ledger (`localhost:8081`)

Swagger UI: http://localhost:8081/swagger-ui.html

#### `GET /v1/users/{userId}/transactions` — Transaction history

```bash
curl http://localhost:8081/v1/users/user-001/transactions
```

**Response `200 OK`:**
```json
[
  {
    "transactionId": "txn-abc-123",
    "senderId":      "user-001",
    "receiverId":    "user-002",
    "amount":        150.00,
    "currency":      "USD",
    "status":        "COMPLETED",
    "createdAt":     "2026-08-31T13:00:00Z"
  }
]
```

#### `GET /v1/transactions/{transactionId}` — Get single transaction

```bash
curl http://localhost:8081/v1/transactions/txn-abc-123
```

| Status | Meaning |
|---|---|
| `200 OK` | Transaction found |
| `404 Not Found` | No transaction with this ID |

---

### Service C — Analytics (`localhost:8082`)

Swagger UI: http://localhost:8082/swagger-ui.html

#### `GET /v1/analytics/summary` — Aggregated statistics

```bash
curl http://localhost:8082/v1/analytics/summary
```

**Response `200 OK`:**
```json
{
  "completedPayments": 42,
  "totalVolume":       12500.00
}
```

#### `GET /v1/analytics/events` — Recent payment events

```bash
curl http://localhost:8082/v1/analytics/events
```

**Response `200 OK`:** Array of recorded analytics events with `transactionId`, `amount`, `currency`, `eventType`, `eventTimestamp`.

---

### Health Endpoints (all services)

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

---

## Event Flow

```
Client
  │
  │  POST /v1/payments
  ▼
Service A (Gateway)
  1. Validate request
  2. Redis SETNX idempotency check (key = "idempotency:txn:{id}", TTL = 24h)
     └─ If key exists → return existing DB record (duplicate suppressed)
  3. Save Payment(status=PENDING) to PostgreSQL
  4. Publish PaymentInitiatedEvent → Kafka topic: payment.initiated
  5. Return 202 Accepted
  │
  │  Kafka: payment.initiated
  ▼
Service B (Ledger)
  1. Check for duplicate (transactionId already in transactions table → skip)
  2. Save Transaction(status=PENDING)
  3. SELECT ... FOR UPDATE on sender account (pessimistic write lock)
  4a. Insufficient funds:
       - Save Transaction(status=FAILED, failureReason="Insufficient funds")
       - Publish PaymentFailedEvent → Kafka: payment.failed
  4b. Sufficient funds:
       - Debit sender, credit receiver (atomic @Transactional)
       - Save Transaction(status=COMPLETED)
       - Publish PaymentCompletedEvent → Kafka: payment.completed
  │
  │  Kafka: payment.completed
  ▼
Service C (Analytics)
  1. Check for duplicate (transactionId already recorded → skip)
  2. Insert AnalyticsRecord into analytics.payment_events
```

---

## Configuration

### Environment Variables

All services are configured via environment variables with sensible local defaults.

**Service A — Gateway**

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `swiftpay_gateway` | Database name |
| `DB_USER` | `swiftpay` | Database username |
| `DB_PASS` | `swiftpay` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |

**Service B — Ledger** / **Service C — Analytics**

Same database and Kafka variables (no Redis). Each service points to its own database by name.

### Seed Data (Ledger)

The ledger database is pre-seeded via Flyway migration with test accounts:

| User ID | Balance | Currency |
|---|---|---|
| `user-001` | $10,000.00 | USD |
| `user-002` | $5,000.00 | USD |
| `user-003` | $2,500.00 | USD |
| `user-poor` | $10.00 | USD |

---

## Testing

### Run All Tests

```bash
./mvnw clean test
```

### Test Summary

| Service | Unit Tests | Integration Tests | Total |
|---|---|---|---|
| service-a-gateway | 10 | 9 | **19** |
| service-b-ledger | 4 | 7 | **11** |
| service-c-analytics | 2 | 5 | **7** |
| **Total** | **16** | **21** | **37** |

### Test Strategy

**Unit tests** use Mockito to isolate each class:
- `PaymentControllerTest` — `@WebMvcTest` covering validation, HTTP status codes, error responses
- `PaymentServiceTest` — mocked Redis, Kafka, repository; tests idempotency logic and happy path
- `LedgerServiceTest` — mocked repositories; tests balance deduction, insufficient funds, deduplication
- `AnalyticsServiceTest` — mocked repository; tests record creation and duplicate suppression

**Integration tests** use Spring `@SpringBootTest` with:
- **H2 in PostgreSQL compatibility mode** — no Docker required, schema created via `ddl-auto: create-drop`
- **Spring `@EmbeddedKafka`** — in-process Kafka broker, no external dependency
- **Mockito-backed `RedisTemplate`** — `ConcurrentHashMap` store via `TestRedisConfig`

Test categories covered per the project specification:

| Category | Tests |
|---|---|
| Smoke | Health endpoint returns UP |
| Functional | POST returns 202 PENDING; event recorded in analytics |
| Integration | Payment persisted to DB; Kafka event consumed; history endpoint returns data |
| Negative | Missing sender → 400; negative amount → 400; unknown ID → 404; insufficient funds → FAILED |
| Regression | Swagger UI accessible; duplicate event idempotent; duplicate transaction idempotent |

### Run a Specific Test Class

```bash
./mvnw test -pl service-a-gateway -Dtest=PaymentIntegrationTest
./mvnw test -pl service-b-ledger   -Dtest=LedgerServiceTest
./mvnw test -pl service-c-analytics -Dtest=AnalyticsIntegrationTest
```

### Load test (250 TPS × 1,000,000 transactions)

The harness for the spec's load-test requirement lives in [`load-test/`](load-test/):

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
docker compose up --build -d
./load-test/run-loadtest.sh          # ~5 min warm-up + ~67 min at 250 req/s
```

A completed run is documented in **[`load-test/LOAD_TEST_REPORT.md`](load-test/LOAD_TEST_REPORT.md)**:
999,677 requests at 249.9 req/s, 0 HTTP failures, exact end-to-end reconciliation
(gateway → ledger `COMPLETED` → analytics, all 999,677), money conserved, zero
Kafka retries/dead-letters. Packet capture: a 90 s full-detail window is in
[`load-test/artifacts/`](load-test/artifacts/PCAP.md); the full 49.6 M-packet /
68-minute trace is a [GitHub Release asset](https://github.com/suhasallampalli/swiftpay-pyrogroup-suhasallampalli/releases/tag/loadtest-1M-20260901).

---

## CI/CD

GitHub Actions pipeline defined in `.github/workflows/ci.yml`.

**Triggers:** push to `main` or `develop`; pull requests targeting `main`.

**Pipeline steps:**

1. **Checkout** — fetch repository at the pushed commit
2. **Set up Java 21** — Temurin distribution with Maven cache
3. **Unit tests** — `./mvnw clean test` (all 37 tests, no Docker needed)
4. **Integration tests** — `./mvnw verify` (H2 + EmbeddedKafka)
5. **Build Docker image** — for each of the three services

CI status: ✅ passing on `main`

---

## Design Decisions

**Why pessimistic locking for balance updates?**
The ledger service uses `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) on the sender's account row rather than optimistic locking. For a financial system, a failed optimistic lock would require a full retry of the payment, which is observable to the caller. Pessimistic locking serialises concurrent payments from the same sender, guaranteeing correctness without retries.

**Why Redis for idempotency instead of a database unique constraint?**
The database already has `transaction_id` as a primary key providing hard uniqueness guarantees. Redis provides a fast, TTL-aware first line of defence that short-circuits before any DB write attempt, avoiding unnecessary write pressure and giving instant duplicate detection in O(1).

**Why H2 instead of Testcontainers for integration tests?**
Testcontainers requires a running Docker daemon and a compatible Docker API version. Using H2 in PostgreSQL compatibility mode (`MODE=PostgreSQL`) with `ddl-auto: create-drop` keeps the test suite dependency-free — any developer or CI runner with Java can run `./mvnw test` without Docker.

**Why a separate `common-events` module?**
Kafka producers and consumers must agree on the exact shape of serialised events. Sharing POJOs via a dedicated Maven module enforces this contract at compile time rather than relying on manual synchronisation of duplicated classes across services.

**Why `@RetryableTopic` instead of a manual retry loop?**
`@RetryableTopic` creates dedicated retry and dead-letter topics automatically, preserving the original topic's throughput. Failed messages are retried with exponential backoff without blocking the main consumer thread, and permanently failed messages land in the DLT for inspection.
