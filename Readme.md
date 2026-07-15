# 🏥 MedInfo — Microservices

**An emergency medical information platform**, migrated from a Spring Boot monolith into an event-driven microservices architecture.

In a medical emergency, first responders scan a QR code to instantly access critical health information — blood group, allergies, medications, emergency contacts — **no login required**.

> 📐 Deep-dive documentation: **[Architecture.md](Architecture.md)** — full configs, structure trees, API contracts, and testing details.

---   

## 🏗️ Architecture

```
                                  GitHub
                                     │
                                     ▼
                            GitHub Actions (CI)
                                     │
                                     ▼
                                Docker Hub
                                     │
                                     ▼
                               Railway Cloud
                                     │
                                     ▼
                           API Gateway (8080)
                                     │
                              Eureka Discovery
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        ▼                            ▼                            ▼
  AUTH SERVICE                MEDICAL SERVICE             AUDIT SERVICE
      │                              │                         │
      │                              │                         │
      ▼                              ▼                         ▼
  PostgreSQL                    PostgreSQL               PostgreSQL
    (Neon)                        (Neon)                   (Neon)
                                      │
                       ┌──────────────┴──────────────┐
                       ▼                             ▼
                 Redis Cache                    Kafka Broker

                    ▲
                    │
             Config Server
                    │
                    ▼
        medinfo-config (Git Repository)
```

**Emergency Profile request, in actual order** (the diagram above shows topology, not sequence — Redis is always checked first):
```
QR Scan → Redis GET
   Cache HIT  → Return immediately (no DB, no Feign, no Kafka)
   Cache MISS → Medical DB (find by publicProfileId, no Auth needed)
              → Feign → Auth Service (fullName only)
              → Kafka → audit event (fire-and-forget)
              → cache the response → Return
```

**Three communication styles, each where it belongs:**

| Interaction | Style | Why |
|---|---|---|
| Medical → Redis | Synchronous, in-process | Cache-Aside — checked before anything else on the Emergency Profile path |
| Medical → Auth (resolve fullName) | Synchronous **Feign** | The response needs it — the only thing Medical Service doesn't own itself |
| Medical → Audit (log access, **cache misses only**) | Asynchronous **Kafka** | The response doesn't depend on the audit — fire and forget. Cache hits currently skip this entirely (see note below). |

> ⚠️ **Currently only cache misses are audited.** A cache hit is still a real access to someone's emergency data, but no Kafka event is published for it today — cache-hit auditing is planned, not yet built. Don't read "every emergency access is audited" anywhere in this repo as still accurate; it isn't, until that gap is closed.

> **Request order on the Emergency Profile path:** Redis check → (on miss) Medical DB lookup by `publicProfileId` → Feign to Auth for `fullName` → Kafka audit event → response. Redis is always checked first; Feign to Auth only happens after a Medical DB lookup, and only on a cache miss.

| Service | Port | Owns |
|---|---|---|
| **Config Server** | 8888 | Centralized configuration, backed by a dedicated `medinfo-config` Git repo — every service below fetches its config from here at startup |
| **API Gateway** | 8080 | Single public entry point, `lb://` dynamic routing via Eureka |
| **Eureka Server** | 8761 | Service registry, heartbeats, discovery |
| **Auth Service** | 8081 | Registration, login, JWT generation, minimal internal user-by-id API |
| **Medical Service** | 8082 | Medical profiles (incl. `publicProfileId`), emergency contacts, emergency access, Redis cache, Kafka producer |
| **Audit Service** | 8083 | Audit logging — pure Kafka consumer, no REST API |

> Every service's local config now shrinks to two lines (`spring.application.name` + `spring.config.import=configserver:http://localhost:8888`) — the rest lives in the `medinfo-config` repo. Full reasoning in [Architecture.md](Architecture.md).

---

## ⚙️ Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Cloud
  - Config Server
  - Eureka Server
  - Gateway (reactive, WebFlux)
  - OpenFeign
  - LoadBalancer
- Spring Security + JWT
- PostgreSQL (Neon)
- Redis
- Apache Kafka (KRaft)
- Docker
- Docker Compose
- GitHub Actions
- Docker Hub
- Railway
- JUnit 5
- Mockito
- JaCoCo
- Maven

---

## 🧠 Key Design Decisions

- **One database per service** — `auth_db`, `medical_db`, `audit_db`. No shared tables, no cross-service queries.
- **Decentralized JWT validation** — only Auth Service issues tokens; every service validates independently with a shared signing secret. Custom claims (`userId`, `role`) mean zero database lookups downstream.
- **No cross-service JPA relationships** — `@ManyToOne User` became `Long userId`. The owning service is reached via API, never via its database.
- **Sync vs async by one question:** does the response depend on the result? Identity resolution → Feign. Audit logging → Kafka.
- **Feign is now identity-only, not user-resolution.** Since the `publicProfileId` ownership redesign, `AuthClient` resolves `fullName` alone — Medical Service resolves its own `publicProfileId` → `MedicalProfile` locally, with zero Auth Service dependency for the medical-data portion of a response. An Auth outage today only costs one field, not the whole lookup.
- **Shared event contract via `medinfo-common`** — `AuditLogEvent` lives in a shared module and is imported by both producer (Medical Service) and consumer (Audit Service). Cross-service deserialization is handled by explicitly whitelisting `com.medinfo.common.events` in the consumer's trusted packages, not by duplicating the class.
- **Reliable by design, not by luck** — bounded retry (3× with backoff) → Dead Letter Topic for poison messages, and idempotent consumption (unique `eventId` + DB constraint) since Kafka is at-least-once and duplicates are normal.
- **Bounded contexts** — audit logging was extracted from the Medical Service into its own service with its own database, evolving from local persistence → Feign call → Kafka event.
- **Cache-Aside on the highest-traffic endpoint** — the public, unauthenticated Emergency Profile API is fronted by Redis; PostgreSQL always stays the source of truth. **Explicit cache eviction on profile update is the primary consistency mechanism**; a 10-min TTL is a secondary safety net for a missed or buggy eviction path, not the primary mechanism itself. (Note: `deleteProfile()` does not currently evict the cache — only `updateProfile()` does; see Architecture.md's known-issues notes.)
- **A cache key belongs to the service that owns the resource** — building Redis eviction exposed that `publicProfileId` was owned by Auth Service while Medical Service needed it to invalidate its own cache. Fixed by moving `publicProfileId` to Medical Service, while `fullName` deliberately stayed in Auth Service as identity data. See [Architecture.md](Architecture.md) for the full reasoning.
- **Business logic unit tested in isolation** — mocked repositories, mocked Feign clients, mocked SecurityContext, mocked cache service. No DB, no HTTP, no Spring context. JaCoCo coverage on all three business services.
- **Configuration is centralized, not fully removed** — every service's local config shrank to two lines (`spring.application.name`, `spring.config.import`); everything else moved to a dedicated `medinfo-config` Git repo, fetched via Spring Cloud Config Server at startup. This centralizes almost all runtime configuration, not literally all of it — those two lines still exist locally per service, by design. Trade-off named directly: this removes the bulk of the duplication but makes Config Server a new hard startup dependency for every other service.
- **A 5-layer logging strategy, with an explicit rule for each layer** — Request (filter: method/URL/IP/status/timing) → Controller (almost nothing) → Service (⭐ most logs live here) → Repository (almost never) → Infrastructure (startup/connection events only). Log level is a signal, not decoration: `WARN` means "unexpected but handled correctly" (cache miss, duplicate Kafka event, retry attempt), `ERROR` means "actually failed" (Feign unreachable, event moved to DLT, unhandled exception).

---

## ☁️ Production Deployment

The entire platform is deployed to Railway using Docker images published automatically by GitHub Actions.

**Deployment Architecture:**
```
Git Push
      │
      ▼
GitHub Actions
      │
      ▼
Build (mvn clean package -DskipTests)
      │
      ▼
Docker Image Build
      │
      ▼
Push Images to Docker Hub
      │
      ▼
Railway Deployment (manual)
```

**Production services include:**
- Config Server
- Eureka Server
- API Gateway
- Auth Service
- Medical Service
- Audit Service
- Redis
- Kafka
- PostgreSQL (Neon)

Configuration is fully externalized using Railway Environment Variables and Spring Cloud Config Server.

> ⚠️ CI currently builds and publishes images but **does not run the test suite** (`-DskipTests`), and Railway deployment is still a manual step, not automated CD. See Architecture.md's CI/CD section.

---

## 🐳 Docker

Every Spring Boot service is containerized using Docker.

The local development environment is orchestrated through Docker Compose.

**Containers include:**
- Config Server
- Eureka Server
- Gateway
- Auth Service
- Medical Service
- Audit Service
- Kafka
- Kafka UI
- Redis

Health checks ensure services only start after their dependencies become healthy.

---

## ⚙️ Continuous Integration

GitHub Actions automatically performs:

✔ Maven Build (`mvn clean package -DskipTests`)

✔ Docker Image Build

✔ Docker Hub Push

Every commit to `main` (also triggers on `docker`, `ci-cd`) produces production-ready Docker images, tagged `latest`.

---

## 🔧 Real Issues Hit & Fixed

These came from actually running the system, not from a tutorial:

1. **`UnknownHostException` at the Gateway** — Eureka registered services under the machine's corporate hostname, which couldn't be resolved locally. Fixed at the time with `eureka.instance.prefer-ip-address=true` on every service.
2. **Kafka trusted-packages deserialization failure** — the JSON deserializer embeds the producer's class name in message headers by default; without an explicit allowlist, the consumer refuses to deserialize a class from a package it doesn't trust. Fixed by adding `com.medinfo.common.events` to `spring.kafka.consumer.properties.spring.json.trusted.packages` on Audit Service — `AuditLogEvent` is a single shared class in `medinfo-common`, imported by both producer and consumer; the fix was trusting the package, not duplicating the class.
3. **Feign ErrorDecoder never fires on connection failures** — it only processes HTTP responses. A downed service throws `RetryableException` with no HTTP response at all, so decoder-based handling must be paired with service-level handling (→ 503). Long-term home: Resilience4j circuit breakers.
4. **Redis deserialization failure — missing no-args constructor** — Jackson couldn't reconstruct cached DTOs (`Cannot construct instance... no default constructor`). Fixed by adding `@NoArgsConstructor` alongside the existing `@Builder`/`@AllArgsConstructor`.
5. **Gateway never routed `/api/profile`** — discovered while verifying the Redis/ownership redesign end-to-end. `MedicalProfileController` worked when hit directly on port 8082 but 404'd through the Gateway, since the route predicate only matched `/api/medical/**`. Fixed by adding `/api/profile/**` to the route.
6. **Spring Security filter registration order** — wiring in `RequestLoggingFilter` via `addFilterBefore(requestLoggingFilter, JWTAuthenticationFilter.class)` failed at startup with `"The Filter class ... does not have a registered order"`, in both `auth-service` and `medical-service`. A custom filter has no implicit chain position — it only gets one once it's explicitly registered, and that has to happen *before* anything else references it as an anchor. Fixed by registering `jwtAuthenticationFilter`'s own position first.
7. **A stale test nobody had compiled since the Kafka migration** — `audit-service`'s `AuditServiceTest` still imported a `CreateAuditLogRequestDTO`/`Enum.AccessMethod` pair deleted back when the REST-based audit endpoint was replaced with Kafka. Never caught because nothing had run a full build across every module (vs. just the ones being actively edited) until this was rewritten against the real `createAuditLog(AuditLogEvent)` API.

**Docker Networking**

8. **Kafka connection failure after Dockerization** — Producer and Consumer were initially configured using `localhost:9092`. Inside Docker, each container has its own `localhost`, so the broker was unreachable from other containers. Fixed by moving the Kafka bootstrap servers to centralized configuration and injecting them via `@Value` instead of hardcoding.

**Docker Health Checks**

9. **Incorrect startup ordering** — Docker Compose initially used `service_started`, so services attempted to connect before their dependencies' Spring Boot applications had actually finished starting. Fixed by introducing Docker health checks against `/actuator/health` and switching dependencies to `condition: service_healthy`.

**GitHub Actions**

10. **Docker build failed in CI** — the Config Server JAR could not be copied during the Docker build. Root cause: Config Server was missing from the parent Maven reactor. Fixed by adding the Config Server module to the parent `pom.xml`.

**Railway Deployment**

11. **Gateway returned HTTP 500** — the Gateway failed to resolve services using `lb://AUTH-SERVICE`. Root cause: Gateway was missing Spring Cloud LoadBalancer. Fixed by adding `spring-cloud-starter-loadbalancer`, which restored Eureka-based service discovery.

---

## 🧪 Testing

```bash
mvn clean test
# JaCoCo HTML report → target/site/jacoco/index.html (per service)
```

JUnit 5 + Mockito across auth, medical, and audit services — success **and** failure paths: duplicates, missing resources, invalid credentials, cross-user unauthorized access, downstream service unavailability, repository exceptions. Full scenario lists in [Architecture.md](Architecture.md).

> ⚠️ CI currently skips this suite (`-DskipTests`) — tests pass locally but don't yet gate merges.

---

## 🚀 Running Locally

```bash
# 1. Start infrastructure — Kafka (KRaft, no ZooKeeper), Kafka UI, and Redis together
docker compose -f infrastructure/docker-compose.yml up -d
# Kafka broker: localhost:9092 · Kafka UI: localhost:8085 · Redis: localhost:6379

# 2. Start services in order — Config Server MUST be first
config-server      # 8888 — every other service fetches its config from here
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
| Medical profile CRUD | `/api/profile` |
| Emergency contacts CRUD | `/api/contacts` |
| **Emergency access (public, no login)** | `GET /api/emergency/{publicProfileId}` |

---

## ✅ Progress

- [x] Monolith → Microservices
- [x] Database per Service
- [x] JWT Authentication
- [x] Spring Cloud Gateway
- [x] Eureka Service Discovery
- [x] OpenFeign
- [x] Kafka Event-Driven Architecture
- [x] Kafka Retry + Dead Letter Topic
- [x] Redis Cache (Cache Aside)
- [x] Config Server
- [x] Docker
- [x] Docker Compose
- [x] Docker Health Checks
- [x] Externalized Configuration (.env + Railway Variables)
- [x] GitHub Actions CI
- [x] Docker Hub Image Publishing
- [x] Railway Cloud Deployment (manual)
- [x] Centralized Logging
- [x] Unit Testing + JaCoCo

### 🚧 Next Improvements

- [ ] Resilience4j Circuit Breaker
- [ ] Cache-hit audit logging
- [ ] Tests gating CI (currently skipped via `-DskipTests`)
- [ ] Prometheus + Grafana Monitoring
- [ ] Distributed Tracing (Zipkin/OpenTelemetry)
- [ ] ELK / Loki Centralized Logs
- [ ] Automated CD to Railway
- [ ] Swagger / OpenAPI

---

## 📖 The Story Behind It

This project was built after I underwent ENT surgery with a hypertension history and realized: if I'd been unconscious, no one would have known my medical history. MedInfo is both a portfolio project and a real product idea — and this repository documents its evolution from monolith to production-style microservices, one architectural decision at a time.
