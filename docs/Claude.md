Project

SwiftPay — a real-time P2P payment ledger built for a 2-day hackathon. Full requirements: docs/PROJECT_SPEC.md.

Resolved Decisions (spec left these open — do not re-litigate)
Area	Decision	Why
Language / Framework	Java 21, Spring Boot 3	LTS Java, larger ecosystem/docs than Quarkus for a fast build
Local infra	docker-compose.yml only (no K8s manifests)	Fastest path to "does it start" for hackathon scope; K8s manifests are a stretch goal, not core deliverable
Service C analytics store	Mock analytics table in PostgreSQL (a separate analytics schema/table), not real ClickHouse	Avoids standing up a 4th piece of infra for a bonus service
Testing	Testcontainers for integration tests (Postgres, Kafka, Redis)	Matches spec's suggestion, gives real integration confidence
Load test (250 TPS / 1M txns + PCAP)	Out of scope by default — flagged as optional stretch goal	Needs dedicated infra/time; do not attempt unless explicitly asked
Idempotency store	Redis, key = transaction_id, TTL = 24h	Matches spec exactly

If any of these need to change, update this table — don't silently diverge.

Module Layout
swiftpay/
  service-a-gateway/      # Transaction Gateway (REST API, producer)
  service-b-ledger/       # Ledger Service (Kafka consumer, processor, history API)
  service-c-analytics/    # Analytics Worker (Kafka consumer -> mock OLAP table)
  docker-compose.yml       # Postgres + Kafka + Redis + all 3 services
  .github/workflows/       # CI: build, test, docker build
  docs/
    PROJECT_SPEC.md

Each service is its own Maven/Gradle module with its own Dockerfile. Shared event schemas (e.g. PaymentInitiated, PaymentCompleted, PaymentFailed) should live in a small shared library module (common-events) rather than being duplicated per service.

Build & Run
Build all: ./mvnw clean install (or ./gradlew build, whichever gets scaffolded — record the actual choice here once made)
Run everything locally: docker-compose up --build
Run one service standalone for dev: cd service-a-gateway && ./mvnw spring-boot:run
Run tests: ./mvnw test (unit) — integration tests use Testcontainers and require Docker to be running
API Conventions
All REST endpoints documented via springdoc-openapi (Swagger UI at /swagger-ui.html per service)
Standard error response shape: { "timestamp", "status", "error", "message", "path" }
Health check at /health (Spring Actuator) on every service
Definition of Done (end-to-end)

The workflow is considered working when, with docker-compose up:

A POST /v1/payments to Service A with a valid sender/receiver/amount returns 202 Accepted with status PENDING.
Service B consumes the event, performs the atomic debit/credit, and the transaction's status becomes COMPLETED (verifiable via Service B's history GET endpoint).
Re-sending the same transaction_id within 24h is rejected/no-ops due to Redis idempotency check, not double-processed.
A payment where the sender has insufficient balance results in a PaymentFailed event and a FAILED status, not a silent failure or crash.
Service C picks up PaymentCompleted events and records them in the mock analytics table.
Killing and restarting the Postgres container mid-flow does not corrupt data — Kafka consumer retry logic recovers cleanly.

Claude Code should verify each of these by actually running the stack and hitting the endpoints (curl or the Swagger UI), not by inspecting code alone.

Notes
Keep layers separated per service: controller → service → repository, with Kafka producers/consumers as their own components.
Meaningful naming over cleverness — this is a submission that will be read by reviewers.