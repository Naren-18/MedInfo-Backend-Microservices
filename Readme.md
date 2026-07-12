# 🏥 MedInfo — Microservices

**An emergency medical information platform**, migrated from a Spring Boot monolith into an event-driven microservices architecture.

In a medical emergency, first responders scan a QR code to instantly access critical health information — blood group, allergies, medications, emergency contacts — **no login required**.

> 📐 Deep-dive documentation: **[Architecture.md](Architecture.md)** — full configs, structure trees, API contracts, and testing details.

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
        ▲              │  │ ▲               ▲
        └────Feign─────┘  │ └─Redis (6379)  │
      (fullName only)     │  Cache-Aside    │
                          │  Kafka topic    │
                          └─► emergency- ───┘
                             access-events

   auth_db         medical_db          audit_db          Redis
```

**Three communication styles, each where it belongs:**

| Interaction | Style | Why |
|---|---|---|
| Medical → Redis | Synchronous, in-process | Cache-Aside — checked before anything else on the Emergency Profile path |
| Medical → Auth (resolve fullName) | Synchronous **Feign** | The response needs it — the only thing Medical Service doesn't own itself |
| Medical → Audit (log access) | Asynchronous **Kafka** | The response doesn't depend on the audit — fire and forget |

| Service | Port | Owns |
|---|---|---|
| **API Gateway** | 8080 | Single public entry point, `lb://` dynamic routing via Eureka |
| **Eureka Server** | 8761 | Service registry, heartbeats, discovery |
| **Auth Service** | 8081 | Registration, login, JWT generation, minimal internal user-by-id API |
| **Medical Service** | 8082 | Medical profiles (incl. `publicProfileId`), emergency contacts, emergency access, Redis cache, Kafka producer |
| **Audit Service** | 8083 | Audit logging — pure Kafka consumer, no REST API |

---

## ⚙️ Tech Stack

Java 21 · Spring Boot 3.5 · Spring Cloud (Gateway, Eureka, OpenFeign) · Spring Security + JWT · Apache Kafka (KRaft) · Redis · PostgreSQL (Neon) · JUnit 5 + Mockito + JaCoCo · Maven

---

## 🧠 Key Design Decisions

- **One database per service** — `auth_db`, `medical_db`, `audit_db`. No shared tables, no cross-service queries.
- **Decentralized JWT validation** — only Auth Service issues tokens; every service validates independently with a shared signing secret. Custom claims (`userId`, `role`) mean zero database lookups downstream.
- **No cross-service JPA relationships** — `@ManyToOne User` became `Long userId`. The owning service is reached via API, never via its database.
- **Sync vs async by one question:** does the response depend on the result? User resolution → Feign. Audit logging → Kafka.
- **Events are the contract, not Java classes** — producer and consumer each own their event class copy; only the JSON shape is shared.
- **Reliable by design, not by luck** — bounded retry (3× with backoff) → Dead Letter Topic for poison messages, and idempotent consumption (unique `eventId` + DB constraint) since Kafka is at-least-once and duplicates are normal.
- **Bounded contexts** — audit logging was extracted from the Medical Service into its own service with its own database, evolving from local persistence → Feign call → Kafka event.
- **Cache-Aside on the highest-traffic endpoint** — the public, unauthenticated Emergency Profile API is fronted by Redis; PostgreSQL always stays the source of truth, and a 10-min TTL backstops explicit eviction on update.
- **A cache key belongs to the service that owns the resource** — building Redis eviction exposed that `publicProfileId` was owned by Auth Service while Medical Service needed it to invalidate its own cache. Fixed by moving `publicProfileId` to Medical Service, while `fullName` deliberately stayed in Auth Service as identity data. See [Architecture.md](docs/ARCHITECTURE.md) for the full reasoning.
- **Business logic unit tested in isolation** — mocked repositories, mocked Feign clients, mocked SecurityContext, mocked cache service. No DB, no HTTP, no Spring context. JaCoCo coverage on all three business services.

---

## 🔧 Real Issues Hit & Fixed

These came from actually running the system, not from a tutorial:

1. **`UnknownHostException` at the Gateway** — Eureka registered services under the machine's corporate hostname, which couldn't be resolved locally. Fixed with `eureka.instance.prefer-ip-address=true` on every service.
2. **Kafka trusted-packages deserialization failure** — the JSON deserializer embeds the *producer's* class name in message headers, which the consumer's classpath doesn't (and shouldn't) contain. Fixed by giving the Audit Service its own event class copy and disabling type headers — the JSON contract is shared, not the Java class.
3. **Feign ErrorDecoder never fires on connection failures** — it only processes HTTP responses. A downed service throws `RetryableException` with no HTTP response at all, so decoder-based handling must be paired with service-level handling (→ 503). Long-term home: Resilience4j circuit breakers.
4. **Redis deserialization failure — missing no-args constructor** — Jackson couldn't reconstruct cached DTOs (`Cannot construct instance... no default constructor`). Fixed by adding `@NoArgsConstructor` alongside the existing `@Builder`/`@AllArgsConstructor`.
5. **Gateway never routed `/api/profile`** — discovered while verifying the Redis/ownership redesign end-to-end. `MedicalProfileController` worked when hit directly on port 8082 but 404'd through the Gateway, since the route predicate only matched `/api/medical/**`. Fixed by adding `/api/profile/**` to the route.

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

# 2. Start Redis
docker compose up redis     # localhost:6379

# 3. Start services in order
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
- [x] Redis caching (Cache-Aside, 10-min TTL) on the Emergency Profile API
- [x] Architecture redesign — moved `publicProfileId` ownership from Auth Service to Medical Service
- [ ] Cache-hit audit logging, circuit breaker for the Auth Feign call, `publicProfileId` backfill migration
- [ ] Docker & Docker Compose (Redis already containerized)
- [ ] CI/CD with GitHub Actions
- [ ] Cloud deployment

---

## 📖 The Story Behind It

This project was built after I underwent ENT surgery with a hypertension history and realized: if I'd been unconscious, no one would have known my medical history. MedInfo is both a portfolio project and a real product idea — and this repository documents its evolution from monolith to production-style microservices, one architectural decision at a time.