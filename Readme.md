# 🏥 MedInfo — Microservices

**An emergency medical information platform**, migrated from a Spring Boot monolith into an event-driven microservices architecture.

In a medical emergency, first responders scan a QR code to instantly access critical health information — blood group, allergies, medications, emergency contacts — **no login required**.

> 📐 Deep-dive documentation: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — full configs, structure trees, API contracts, and testing details.

---

## 🏗️ Architecture

```
                           Client
                              │
                              ▼
                     API Gateway (8080)
                              │
                       Eureka Discovery
                              │
        ┌──────────────┬──────────────┐
        ▼              ▼
   AUTH SERVICE   MEDICAL SERVICE      AUDIT SERVICE
     (8081)          (8082)              (8083)
        ▲              │  │                 ▲
        └────Feign─────┘  │                 │
                          │  Kafka topic    │
                          └─► emergency- ───┘
                             access-events

   auth_db         medical_db          audit_db
```

**Two communication styles, each where it belongs:**

| Interaction | Style | Why |
|---|---|---|
| Medical → Auth (resolve user) | Synchronous **Feign** | The response needs the userId — can't proceed without it |
| Medical → Audit (log access) | Asynchronous **Kafka** | The response doesn't depend on the audit — fire and forget |

| Service | Port | Owns |
|---|---|---|
| **API Gateway** | 8080 | Single public entry point, `lb://` dynamic routing via Eureka |
| **Eureka Server** | 8761 | Service registry, heartbeats, discovery |
| **Auth Service** | 8081 | Registration, login, JWT generation, public user API |
| **Medical Service** | 8082 | Medical profiles, emergency contacts, emergency access, Kafka producer |
| **Audit Service** | 8083 | Audit logging — pure Kafka consumer, no REST API |

---

## ⚙️ Tech Stack

Java 21 · Spring Boot 3.5 · Spring Cloud (Gateway, Eureka, OpenFeign) · Spring Security + JWT · Apache Kafka (KRaft) · PostgreSQL (Neon) · JUnit 5 + Mockito + JaCoCo · Maven

---

## 🧠 Key Design Decisions

- **One database per service** — `auth_db`, `medical_db`, `audit_db`. No shared tables, no cross-service queries.
- **Decentralized JWT validation** — only Auth Service issues tokens; every service validates independently with a shared signing secret. Custom claims (`userId`, `role`) mean zero database lookups downstream.
- **No cross-service JPA relationships** — `@ManyToOne User` became `Long userId`. The owning service is reached via API, never via its database.
- **Sync vs async by one question:** does the response depend on the result? User resolution → Feign. Audit logging → Kafka.
- **Events are the contract, not Java classes** — producer and consumer each own their event class copy; only the JSON shape is shared.
- **Reliable by design, not by luck** — bounded retry (3× with backoff) → Dead Letter Topic for poison messages, and idempotent consumption (unique `eventId` + DB constraint) since Kafka is at-least-once and duplicates are normal.
- **Bounded contexts** — audit logging was extracted from the Medical Service into its own service with its own database, evolving from local persistence → Feign call → Kafka event.
- **Business logic unit tested in isolation** — mocked repositories, mocked Feign clients, mocked SecurityContext. No DB, no HTTP, no Spring context. JaCoCo coverage on all three business services.

---

## 🔧 Real Issues Hit & Fixed

These came from actually running the system, not from a tutorial:

1. **`UnknownHostException` at the Gateway** — Eureka registered services under the machine's corporate hostname, which couldn't be resolved locally. Fixed with `eureka.instance.prefer-ip-address=true` on every service.
2. **Kafka trusted-packages deserialization failure** — the JSON deserializer embeds the *producer's* class name in message headers, which the consumer's classpath doesn't (and shouldn't) contain. Fixed by giving the Audit Service its own event class copy and disabling type headers — the JSON contract is shared, not the Java class.
3. **Feign ErrorDecoder never fires on connection failures** — it only processes HTTP responses. A downed service throws `RetryableException` with no HTTP response at all, so decoder-based handling must be paired with service-level handling (→ 503). Long-term home: Resilience4j circuit breakers.

---

## 🧪 Testing

```bash
mvn clean test
# JaCoCo HTML report → target/site/jacoco/index.html (per service)
```

JUnit 5 + Mockito across auth, medical, and audit services — success **and** failure paths: duplicates, missing resources, invalid credentials, cross-user unauthorized access, downstream service unavailability, repository exceptions. Full scenario lists in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 🚀 Running Locally

```bash
# 1. Start Kafka (KRaft mode — no ZooKeeper)
bin/kafka-server-start.sh config/kraft/server.properties

# 2. Start services in order
eureka-server      # 8761 — dashboard at http://localhost:8761
auth-service       # 8081
medical-service    # 8082
audit-service      # 8083
gateway-service    # 8080 — all client traffic goes here
```

All APIs via the Gateway: `http://localhost:8080/api/...` — Postman collection in [`/postman`](postman/).

| Example | Endpoint |
|---|---|
| Register | `POST /api/auth/register` |
| Login (returns JWT) | `POST /api/auth/login` |
| Medical profile CRUD | `/api/medical/profile` |
| Emergency contacts CRUD | `/api/contacts` |
| **Emergency access (public, no login)** | `GET /api/emergency/{publicProfileId}` |

---

## ✅ Progress

- [x] Monolith → microservices migration (auth, medical domains)
- [x] Database-per-service (PostgreSQL via Neon)
- [x] JWT with custom claims + decentralized validation
- [x] OpenFeign inter-service communication + centralized exception framework
- [x] Eureka service discovery — zero hardcoded URLs
- [x] Spring Cloud Gateway — single entry point
- [x] Audit Service extraction (bounded context)
- [x] Unit testing (JUnit 5 + Mockito) + JaCoCo coverage
- [x] Event-driven audit logging with Kafka — synchronous Feign path removed
- [x] Kafka reliability — retry with backoff, Dead Letter Topic, idempotent consumer
- [ ] Redis caching
- [ ] Docker & Docker Compose
- [ ] CI/CD with GitHub Actions
- [ ] Cloud deployment

---

## 📖 The Story Behind It

This project was built after I underwent ENT surgery with a hypertension history and realized: if I'd been unconscious, no one would have known my medical history. MedInfo is both a portfolio project and a real product idea — and this repository documents its evolution from monolith to production-style microservices, one architectural decision at a time.