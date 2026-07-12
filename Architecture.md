# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
├── config-server       # Centralized configuration (Spring Cloud Config Server)
├── eureka-server       # Service Registry (Netflix Eureka)
├── gateway-service     # API Gateway (Spring Cloud Gateway)
├── auth-service        # Authentication & user identity
├── medical-service     # Medical profiles, contacts, emergency access
├── audit-service       # Centralized audit logging (pure Kafka consumer)
├── medinfo-common      # Shared contracts and common classes
├── postman
└── README.md
```

> Configuration itself now lives outside this repo, in a dedicated `medinfo-config` Git repository — see **⚙️ Config Server** below.

**Why a Monorepo?**
- Easier local development
- Simpler GitHub management
- Easier CI/CD during learning
- Common industry approach for medium-sized projects

### Shared Module

```
medinfo-common
    events
        AuditLogEvent.java   (userId, ipAddress, userAgent, accessMethod, accessedAt, eventId)
```

> `eventId` (UUID) was added to `AuditLogEvent` to support idempotent consumption in `audit-service` — see **🛡️ Kafka Reliability**.

---

## 🏗️ Current Architecture

```
                           Client
                              │
                              ▼
                     API Gateway (8080)
                              │
                       Eureka Discovery
                              │
        ┌──────────────┬──────────────┬──────────────┐
        ▼              ▼              ▼
   AUTH SERVICE   MEDICAL SERVICE   AUDIT SERVICE
     (8081)          (8082)            (8083)
        ▲               │  ▲              ▲
        │               │  └──① Redis GET (Cache-Aside, localhost:6379) — checked FIRST
        └────②Feign─────┘                 │
       (fullName only —       ③ AuditLogEvent (cache MISS only)
        cache miss only)                  │
                                          ▼
                                  Apache Kafka
                             emergency-access-events
                                  (localhost:9092)
                                          │
                                          ▼
                                 AuditEventConsumer ──(3 retries)──► DeadLetterPublishingRecoverer
                                          │                                      │
                                  existsByEventId()                             ▼
                                          │                        emergency-access-events.DLT
                                          ▼
                                      audit_db

     auth_db          medical_db         audit_db          Redis (6379)
```
> Numbers show request order on a cache miss: **① Redis (miss) → Medical DB → ② Feign to Auth → ③ Kafka publish.** On a cache hit, only ① happens.

| Interaction | Style | Why |
|---|---|---|
| Medical → Redis | Synchronous, in-process | Cache-Aside — always checked first, before Feign or Kafka |
| Medical → Auth | OpenFeign | Resolving `fullName` — the only remaining thing Medical Service doesn't own (Day 6 narrowed this from full user resolution); only reached on a cache miss |
| Medical → Audit | Kafka, **cache misses only** | Audit logging is asynchronous and should never block the client response. **Cache hits currently publish no audit event** — see Next Phase in the Day 6 section; don't read this as "every access is audited," it isn't yet. |

**Emergency Profile request, in actual order:**
```
QR Scan → Redis GET (emergency-profile::<publicProfileId>)
   Cache HIT  → Return immediately — no DB, no Feign, no Kafka
   Cache MISS → MedicalProfileRepository.findByPublicProfileId() (no Auth dependency)
              → Feign → Auth Service (fullName only)
              → EmergencyContactsRepository
              → Kafka → emergency-access-events (fire-and-forget)
              → cache the response (TTL 10 min) → Return
```
Redis is always checked first. Feign to Auth and the Kafka publish both only happen on a cache miss, and only after the Medical DB lookup — not before it.

**Gateway Request Lifecycle:**
```
Client → API Gateway → Route Matching → Eureka Service Discovery
       → Target Microservice → Business Logic → Response → Client
```

| Service | Port | Owns |
|---|---|---|
| **Config Server** | 8888 | Centralized configuration, backed by the `medinfo-config` Git repo — every service below fetches its config from here at startup (Day 7) |
| **API Gateway** | 8080 | Single public entry point, dynamic routing, Eureka-integrated load balancing |
| **Eureka Server** | 8761 | Service Registry, Heartbeats, Dashboard |
| **Auth Service** | 8081 | User (id, fullName, email, password), Login, Registration, JWT Generation, Spring Security, minimal internal user-by-id API |
| **Medical Service** | 8082 | Medical Profile (incl. `publicProfileId` — moved here Day 6), Emergency Contacts, Emergency Profile APIs, Redis cache, OpenFeign `fullName` resolution, Kafka Producer |
| **Audit Service** | 8083 | Centralized audit logging — pure Kafka consumer, no REST API |

**Core principles:**
- One database per service — services never share or cross-query each other's databases
- Only Auth Service generates JWT tokens — every other service validates independently using a shared signing secret
- Synchronous calls (Feign) only when the response depends on the result; asynchronous events (Kafka) otherwise
- Services locate each other by logical name through Eureka — no hardcoded URLs anywhere
- Clients communicate with ONE endpoint — the Gateway routes everything
- Each business capability lives in its own bounded context — audit logging is not a medical concern
- Business logic is unit tested in isolation — no database, no HTTP, no Spring context
- Shared event contracts simplify communication between services.
- AuditLogEvent lives inside medinfo-common and is shared between producer and consumer.
- Kafka enables asynchronous communication, reducing service coupling and improving system resilience.
- Producer publishes events without knowing which services consume them.
- Consumers independently process events from Kafka topics.
- Consumer Groups and Offsets ensure reliable message consumption.
- A cache key belongs to the service that owns the resource it identifies — if a service can't compute or invalidate its own cache key without calling another service, the identifier is owned by the wrong service (Day 6).
- Configuration is centralized and version-controlled, not duplicated per service — every service fetches its config from Config Server at startup, backed by a dedicated Git repo (Day 7).

Each service has:
- ✅ Independent Spring Boot application
- ✅ Independent Maven project
- ✅ Independent PostgreSQL database (business services)
- ✅ Independent Deployment
- ✅ Clear ownership of its business domain
- ✅ Registered with Eureka Service Registry
- ✅ JUnit 5 + Mockito unit test suite with JaCoCo coverage (business services)


---

## ⚡ Event-Driven Architecture — Kafka (Day 5)

### Why Kafka?

Until now, the Medical Service communicated with the Audit Service using OpenFeign.

```
Medical Service
      │
      ▼
AuditClient
      │
      ▼
Audit Service
      │
      ▼
audit_db
```

Although this implementation worked, it introduced several architectural limitations.

| Problem | Explanation |
|---|---|
| Tight Coupling | Medical Service depended on Audit Service availability |
| Blocking | Emergency Profile API waited until audit logging completed |
| Scalability | Every request generated another synchronous HTTP request |
| Availability | Audit Service downtime directly affected Medical Service |

Audit logging is not part of the primary business transaction.
The Emergency Profile API should return immediately regardless of whether audit logging succeeds.
Kafka provides asynchronous communication, making it a much better fit.

### Event Driven Architecture

Instead of directly calling Audit Service, Medical Service now publishes an event.

```
Medical Service
      │
      ▼
AuditLogEvent
      │
      ▼
Kafka Topic
      │
      ▼
Audit Service
```

Medical Service no longer knows where Audit Service is running.
It simply publishes an event and continues processing.

### Kafka Infrastructure

Kafka was introduced using Docker Compose.

**Components:**
- Apache Kafka Broker
- Kafka UI

**Ports:**
- Kafka Broker — `localhost:9092`
- Kafka UI — `localhost:8084`

Kafka UI allows monitoring:
- Brokers
- Topics
- Messages
- Consumer Groups

### Kafka Broker

**Development environment:**
- 1 Broker

A broker is a Kafka server responsible for storing topics and messages.
One broker is sufficient for local development.
Production environments typically run multiple brokers for replication and fault tolerance.

### Kafka Topic

Created:
- `emergency-access-events`

Topic creation is handled automatically by Spring Boot.

Created:
- `KafkaTopicConfig`

```java
@Bean
public NewTopic auditEventsTopic() {
    return TopicBuilder
            .name(KafkaTopics.AUDIT_EVENTS)
            .partitions(1)
            .replicas(1)
            .build();
}
```

**Topic configuration:**
- Partitions: 1
- Replicas: 1

### Shared Event Contract

Instead of HTTP DTOs, Kafka exchanges events.

Created inside:
- `medinfo-common`

`AuditLogEvent`

**Fields:**
- `userId`
- `ipAddress`
- `userAgent`
- `accessMethod`

Both Medical Service and Audit Service use the same shared contract.

```
Medical Service

AuditLogEvent

Kafka

AuditLogEvent

Audit Service
```

### Kafka Producer

Medical Service now contains:
- `KafkaProducerConfig`

Configured:
- `ProducerFactory`
- `KafkaTemplate`
- `JsonSerializer`

Created:
- `AuditEventProducer`

Method:
- `publishAuditEvent(AuditLogEvent event)`

**Flow:**

```
EmergencyService

↓

AuditEventProducer

↓

KafkaTemplate

↓

Kafka
```

### Updating Emergency Service

Previous implementation:

```
EmergencyService

↓

AuditClient
```

Current implementation:

```
EmergencyService

↓

AuditEventProducer

↓

Kafka
```

Medical Service no longer performs synchronous audit logging.

### Serialization

Producer automatically converts:

```
AuditLogEvent

↓

JSON

↓

Kafka
```

using `JsonSerializer`.

### Kafka Consumer

Audit Service now contains:
- `KafkaConsumerConfig`

Configured:
- `ConsumerFactory`
- `JsonDeserializer`
- Consumer Group
- Listener Container

Trusted packages were configured for safe JSON deserialization.

### Consumer Groups

**Consumer Group:**
- `audit-group`

Kafka tracks offsets for every consumer group.

Example:

```
Offset 0

↓

Consumed

↓

Offset becomes 1
```

If Audit Service restarts:
- Resume from Offset 1

Previously processed messages are not consumed again.

### Kafka Listener

Created:
- `AuditEventConsumer`

```java
@KafkaListener(
        topics = KafkaTopics.AUDIT_EVENTS,
        groupId = "audit-group"
)
```

**Responsibilities:**
- Consume `AuditLogEvent`
- Delegate to `AuditService`

No business logic exists inside the listener.

### Persisting Events

`AuditService` receives:
- `AuditLogEvent`

Converts:

```
AuditLogEvent

↓

AuditLog Entity

↓

AuditRepository

↓

audit_db
```

Audit Service remains the sole owner of audit persistence.

### Removing Feign

After verifying Kafka communication:

Removed from Medical Service:
- `AuditClient`
- `CreateAuditLogRequestDTO`

Removed from Audit Service:
- `AuditController`
- `CreateAuditLogRequestDTO`

Audit Service is now a pure Kafka consumer.

### Final Event Flow

```
Client
    │
    ▼
Medical Service
    │
EmergencyService
    │
    ▼
AuditEventProducer
    │
    ▼
Kafka Broker
    │
    ▼
emergency-access-events
    │
    ▼
AuditEventConsumer
    │
    ▼
AuditService
    │
    ▼
audit_db
```

Medical Service is now completely independent of Audit Service.

---

## 🛡️ Kafka Reliability — Retry, Dead Letter Topic & Idempotent Consumer

### Why This Was Needed

The happy path worked, but one question remained: **what happens when Audit Service receives an event it cannot process?**

Examples: database temporarily unavailable, network interruption, unexpected exception in consumer logic, malformed event payload ("poison message").

Simply consuming Kafka messages is not sufficient for production. Without additional handling:

```
Event fails
    │
    ▼
Retry immediately
    │
    ▼
Fail again
    │
    ▼
Retry forever (blocking the partition)
```

A single bad event can block every event behind it in the same partition. Kafka is also **at-least-once** by default — a consumer crash before the offset commits means the same event is redelivered, which would create **duplicate audit rows** without protection.

Three reliability mechanisms were added to `audit-service`: **bounded retry**, a **Dead Letter Topic**, and an **idempotent consumer**.

### 1. Consumer Retry — `DefaultErrorHandler` + `FixedBackOff`

```java
@Bean
public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
    FixedBackOff backOff = new FixedBackOff(1000L, 3L); // 1s interval, 3 retries
    return new DefaultErrorHandler(recoverer, backOff);
}
```

Registered on the listener container factory:

```java
ConcurrentKafkaListenerContainerFactory<String, AuditLogEvent> factory = ...;
factory.setCommonErrorHandler(errorHandler);
```

**Retry flow:**

```
Receive Event → Listener Exception → Retry #1 → Retry #2 → Retry #3 → Retries Exhausted
```

Tested by temporarily throwing `new RuntimeException("Testing Kafka Retry")` inside the consumer. Logs confirmed: record retried, offset repositioned, backoff applied, retry attempts exhausted before moving on.

### 2. Dead Letter Topic (DLT)

After retries are exhausted, the event is no longer discarded — it's preserved for investigation and replay.

```
Retry Failed
    │
    ▼
Dead Letter Topic
    │
    ▼
Future Investigation → Possible Replay
```

**Topic ownership moved from Medical Service to Audit Service.** Audit Service owns event consumption, retry handling, and dead-letter processing — so topic management belongs there too. `KafkaTopicConfig` (now inside `audit-service`) creates both topics automatically on startup:

```java
@Bean
public NewTopic auditEventsTopic() {
    return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS).partitions(1).replicas(1).build();
}

@Bean
public NewTopic auditEventsDLT() {
    return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS_DLT).partitions(1).replicas(1).build();
}
```

```
KafkaTopics.AUDIT_EVENTS      = emergency-access-events
KafkaTopics.AUDIT_EVENTS_DLT  = emergency-access-events.DLT
```

**Audit Service becomes a producer too.** Publishing to a DLT requires producer capability, so a `KafkaProducerConfig` (`ProducerFactory` + `KafkaTemplate`) was added inside Audit Service — used internally by `DeadLetterPublishingRecoverer` to publish failed messages, not by application code directly.

**`DeadLetterPublishingRecoverer`:**

```
Failed Event → Determine Destination Topic → Publish Failed Record → Dead Letter Topic
```

Configured to publish to `emergency-access-events.DLT` while **preserving the original partition**, keeping partition consistency between the source topic and the DLT.

**Updated error handling flow:**

```
// Before
DefaultErrorHandler → Retry → Stop (message discarded)

// After
DefaultErrorHandler → Retry → DeadLetterPublishingRecoverer → DLT
```

**Verified:** with an intentional exception left in place, Kafka UI confirmed the failed event landed in `emergency-access-events.DLT` — **while the client still received a successful HTTP response.** This is the core payoff of asynchronous communication: audit logging failures never touch the client's request lifecycle (contrast with the old synchronous Feign path, where `Medical Service → Audit Service → Failure → HTTP 500` would have broken the client response directly).

### 3. Idempotent Consumer

Kafka's at-least-once delivery means the same event can be redelivered:

```
Event Saved → Consumer Crash → Offset Not Committed → Kafka Redelivers Event
```

Without protection, this produces duplicate audit rows.

**Event identity — `eventId` added to `AuditLogEvent`** (shared contract in `medinfo-common`):

```java
public class AuditLogEvent {
    private UUID eventId;   // ← new
    private Long userId;
    private String ipAddress;
    private String userAgent;
    private AccessMethod accessMethod;
    private Instant accessedAt;
}
```

Generated in Medical Service before publishing:

```java
AuditLogEvent event = AuditLogEvent.builder()
        .eventId(UUID.randomUUID())
        .userId(userId)
        // ...
        .build();
```

Every produced event now has a permanent, globally unique identity.

**Persistence — `AuditLog` entity enhanced with `eventId`:**

```java
@Column(nullable = false, unique = true)
private UUID eventId;
```

Database-level `UNIQUE` + `NOT NULL` constraint guarantees duplicates cannot create duplicate rows even under a race.

**Repository:**

```java
boolean existsByEventId(UUID eventId);
```

**Idempotent processing flow:**

```
Receive Event → existsByEventId()
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
   Already Exists          Not Found
        │                       │
        ▼                       ▼
   Ignore Event            Save Audit Log
```

**Tested** by temporarily hardcoding a fixed UUID in the producer and publishing the same event twice. First delivery: not found → saved. Second delivery: already exists → ignored. Logs confirmed `Duplicate event ignored: 11111111-1111-1111-1111-111111111111`; database verification showed **one event → one row**.

### Final Kafka Reliability Architecture

```
Medical Service → Kafka Producer → emergency-access-events
                                          │
                                          ▼
                                   Audit Consumer
                                          │
                                  existsByEventId()
                             ┌────────────┴────────────┐
                             ▼                          ▼
                            No                          Yes
                             │                           │
                             ▼                           ▼
                       Save Audit Log              Ignore Event
                             │
                        Exception?
                             │
                             ▼
                   Retry (3 attempts, 1s backoff)
                             │
                        Still Fails?
                             │
                             ▼
                DeadLetterPublishingRecoverer
                             │
                             ▼
                  emergency-access-events.DLT
```

### Before / After

| Before Reliability | After Reliability |
|---|---|
| Kafka Producer | + Fixed Retry Strategy (`DefaultErrorHandler` + `FixedBackOff`) |
| Kafka Consumer | + Dead Letter Topic + `DeadLetterPublishingRecoverer` |
| Basic Event Processing | + Dedicated Kafka Producer inside Audit Service |
| | + Idempotent Consumer / Duplicate Event Detection |
| | + Database-level uniqueness on `eventId` |
| | + Production-ready failure recovery |

This completes a production-grade Kafka consumer: capable of surviving transient failures, preserving unprocessable events instead of losing them, and safely handling duplicate delivery without corrupting `audit_db`.

---

## 🗄️ Redis Caching & Architecture Refactoring (Day 6)

### Why Redis?

`GET /api/emergency/{publicProfileId}` is public, unauthenticated, and accessed via QR code scans — expected to be the highest-traffic endpoint in the system, and the same profile can be scanned repeatedly in a short window (multiple first responders). Without caching, every request paid for a Feign call to Auth plus two PostgreSQL queries, even though emergency data (blood group, allergies, medications, contacts) changes rarely.

```
Emergency API → Call Auth Service (Feign) → Query Medical Profile → Query Emergency Contacts → Build Response → Return
```

High read frequency + low write frequency is a textbook caching case. Redis fits because it's an in-memory key-value store and the access pattern — fetch by one known key — maps directly onto Redis's GET/SET model.

### Cache-Aside Pattern

Chosen over write-through or read-through:

```
Client → Emergency API → Redis
                            │
                          Hit?
                    ┌───────┴───────┐
                   Yes              No
                    │                │
                 Return          PostgreSQL → Build Response → Save to Redis → Return
```

| Property | Benefit |
|---|---|
| Simple to implement | No cache-provider write hooks, no dual-write complexity |
| Redis is never the source of truth | PostgreSQL always owns the data — Redis can be flushed entirely with zero data loss |
| Cache populated lazily | Only profiles that are actually scanned get cached, no wasted memory pre-warming |
| Explicit invalidation | The application decides exactly when an entry goes stale, not a cache provider's heuristics |

**Docker Compose:**
```yaml
redis:
  image: redis:7.2-alpine
  container_name: redis
  ports:
    - "6379:6379"
  restart: unless-stopped
```
Runs at `localhost:6379`, matched by `medical-service`'s `spring.data.redis.host` / `spring.data.redis.port`.

### Spring Boot Redis Integration

Dependency: `spring-boot-starter-data-redis`.

```java
@Configuration
@EnableCaching
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
```

**Why two serializers?** Redis stores raw bytes — every read/write round-trips `Java Object → JSON → Redis` and back. Keys use `StringRedisSerializer` because cache keys (`emergency-profile::<publicProfileId>`) should stay plain, readable strings. Values use `GenericJackson2JsonRedisSerializer`, which embeds the fully-qualified class name into the stored JSON so deserialization can reconstruct the right DTO type without the caller specifying it.

### ⚠️ Real Issue Hit — Missing No-Args Constructor

```
Cannot construct instance of `com.medinfo.medical.DTO.EmergencyProfileResponseDTO`
(no Creators, like default constructor, exist)
```

**Root cause:** Jackson instantiates via a no-args constructor then populates fields — `EmergencyProfileResponseDTO` and `EContactsDTO` only had `@Builder` + `@AllArgsConstructor`, no default constructor.

**Fix:** added `@NoArgsConstructor` to both DTOs, alongside the existing `@Builder`/`@AllArgsConstructor` — they coexist fine since they serve different callers (service code vs. Jackson).

```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EmergencyProfileResponseDTO {
    private String fullName;
    private Integer age;
    // ...
    private List<EContactsDTO> emergencyContacts;
}
```

### Refactor: Single Responsibility

The first working version put Redis logic directly inside `EmergencyService` (`Redis GET → Database → Redis SET`). It worked, but the service became responsible for two unrelated things — business logic and cache infrastructure (key formatting, serialization, TTL) — harder to test and harder to change independently.

**Extracted `EmergencyProfileCacheService`:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyProfileCacheService {
    private final RedisTemplate<String,Object> redisTemplate;

    private String getCacheKey(String publicProfileId) {
        return "emergency-profile::" + publicProfileId;
    }

    public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId){
        return (EmergencyProfileResponseDTO) redisTemplate.opsForValue().get(getCacheKey(publicProfileId));
    }

    public void cacheEmergencyProfile(String publicProfileId, EmergencyProfileResponseDTO response){
        redisTemplate.opsForValue().set(getCacheKey(publicProfileId), response, Duration.ofMinutes(10));
    }

    public void evictEmergencyProfile(String publicProfileId){
        redisTemplate.delete(getCacheKey(publicProfileId));
    }
}
```

```
EmergencyService → EmergencyProfileCacheService → Redis
```

`EmergencyService` now only orchestrates business logic and calls this service — it has no idea Redis exists underneath. **Benefit realized immediately:** unit tests for `EmergencyService` mock `EmergencyProfileCacheService` as a single collaborator — they never touch `RedisTemplate` directly, and swapping the cache implementation later only touches this one class.

### Cache Key, Hit/Miss, Eviction, TTL

**Key:** `emergency-profile::<publicProfileId>` — exactly what the client sends in the URL, no transformation needed.

**Hit:** no database query, no Feign call, no Kafka event — served entirely from memory.

**Eviction on update** (`MedicalProfileService.updateProfile()`):
```
Update PostgreSQL → Delete Redis Key → Next Request → Cache MISS → Fresh Data
```
Prevents a first responder from ever seeing stale medical information (e.g. an outdated allergy list) — correctness matters more than hit ratio here. **Explicit cache eviction is the primary consistency mechanism for this cache** — it's what guarantees a post-update read is fresh, not a side effect of the cache eventually expiring.

**TTL — implemented as a secondary safety net, not the primary invalidation mechanism:**
```java
redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(10));
```
Every entry expires after 10 minutes regardless of whether eviction fired. Eviction is triggered by application code, and application code can have bugs or missed call sites — TTL bounds how long a forgotten eviction path can serve stale data. A backstop, not a substitute for explicit eviction.

### The Architecture Flaw Redis Exposed

Redis didn't cause this problem — it made an existing design smell impossible to ignore.

**Before:** `publicProfileId` lived on `User` (Auth Service). Medical Service always resolved it via a Feign call before doing anything — circuitous but functionally fine, since every read needed that round trip anyway.

**What changed:** eviction needed `publicProfileId` inside `MedicalProfileService.updateProfile()`, which only had `userId` (from the JWT) — `publicProfileId` lived in Auth Service's database, which Medical Service cannot query directly.

```
MedicalProfileService.updateProfile() → Need: publicProfileId (to evict the right key)
                                       → Have: userId only
                                       → ❌ Cannot resolve without another cross-service call
```

**The problem, named precisely:** the service that owns the Emergency Profile (Medical Service) did not own the identifier used to look it up (`publicProfileId`, owned by Auth Service).

| Bounded Context | Owns |
|---|---|
| Identity (Auth Service) | Login, password, JWT, roles |
| Emergency Profile (Medical Service) | Blood group, allergies, medications, emergency contacts, the identifier used to look them up |

`publicProfileId` is an Emergency Profile concept — it exists for QR-code lookups of medical data, nothing to do with authentication. It was living in the wrong bounded context; Redis was simply the first feature requiring Medical Service to *act on* data it didn't own, rather than just read it once per request.

### Redesign: Moving `publicProfileId`

**Decision:** move `publicProfileId` from `auth-service.User` to `medical-service.MedicalProfile`. Medical Service generates and owns it.

**Auth Service — removed:** `publicProfileId` field from `User`, `UserRepository.findByPublicProfileId(...)`, `AuthService.getUserByPublicProfileId(...)`, and the `GET /api/users/public/{publicProfileId}` endpoint entirely.

**Medical Service — added**, generated once at profile creation:
```java
MedicalProfile medicalProfile = MedicalProfile.builder()
        // ... medical fields ...
        .userId(userId)
        .publicProfileId(UUID.randomUUID().toString())
        .build();
```
Column: `unique = true, nullable = false`. New repository method: `findByPublicProfileId(String publicProfileId)`.

**Result:** `MedicalProfileService.updateProfile()` evicts its own cache without asking anyone, and the Emergency API resolves the medical-data portion of the response with **no Feign call, no dependency on Auth Service being up**, just to find its own resource. Strictly better regardless of caching — Redis just made the cost of not fixing it immediate and visible.

**Stated plainly, since it's the entire point of this redesign: Feign is now used for identity data only.** `AuthClient` has exactly one remaining job — resolving `fullName` by `userId`. All medical data (`publicProfileId`, blood group, allergies, medications, contacts) resolves entirely within `medical_db`, with zero dependency on Auth Service being reachable.

### The `fullName` Question — Why Identity Stays in Auth Service

Natural follow-up: should `fullName` move too, for a zero-cross-service-call resolution? **Rejected.**

| Option | Description | Verdict |
|---|---|---|
| A — Independent field | Medical Service collects its own `fullName` at profile creation, unrelated to the Auth account | Rejected — two names for one person with no defined relationship |
| B — Auth is source of truth, fetched live | Medical Service never stores `fullName`; asks Auth by `userId` on every cache miss | ✅ Chosen |
| C — Auth is source of truth, replicated via Kafka event | Auth publishes `UserUpdated`; Medical keeps a local read-replica | Rejected for now — new event/consumer + eventual-consistency window for a field accessed once per cache-miss, not worth it yet |

`fullName` is identity data, not an Emergency Profile concept — `bloodGroup` and `publicProfileId` are, `fullName` isn't. Same principle applied consistently: each service owns its own data, others request it, never copy it. (A leftover redundant `fullname` column on `MedicalProfile` from an earlier iteration was found and removed while confirming this.)

**New minimal Auth API**, replacing the retired public-profile-based lookup, keyed on the identifier Medical Service actually has after resolving its own profile:
```java
@GetMapping("/internal/users/{userId}")
public ResponseEntity<UserBasicResponseDTO> getUserById(@PathVariable Long userId){
    return ResponseEntity.ok(authService.getUserById(userId));
}
```
Full path `/api/auth/internal/users/{userId}`, marked `permitAll()` — matching how the old lookup actually behaved (service-to-service Feign calls never carried a JWT; it was never really "protected," just not publicly advertised).

### ⚠️ Real Issue Hit — Feign Failures Still Need Handling

Even after the ownership fix, a live Feign call to Auth Service can still fail if Auth is down:
```java
try {
    user = authClient.getUserById(userId);
} catch (RetryableException ex) {
    throw new ServiceUnavailableException("Auth Service is not available");
}
```
`ServiceUnavailableException` → 503 was re-added to `GlobalExceptionHandler`. The failure mode is now **narrower** than before the redesign: Auth being down only breaks the `fullName` field, not the entire lookup — medical data resolves with zero Auth dependency.

### ⚠️ Real Issue Hit — Gateway Never Routed `/api/profile`

Verifying the redesign end-to-end surfaced a pre-existing, unrelated bug: `MedicalProfileController` is mapped at `/api/profile`, but the Gateway's route predicate only forwarded `/api/medical/**` — so profile CRUD 404'd through the Gateway despite working when hit directly on port 8082.
```diff
- Path=/api/medical/**,/api/contacts/**,/api/emergency/**
+ Path=/api/profile/**,/api/contacts/**,/api/emergency/**
```

### Final Emergency Profile Flow (with Redis)

```
QR Scan → publicProfileId → Redis GET (emergency-profile::<publicProfileId>)
  Cache HIT?  ──Yes──► Return cached response (no DB, no Feign, no Kafka)
     No
     ↓
  MedicalProfileRepository.findByPublicProfileId() → MedicalProfile → userId
     ↓
  Feign → Auth Service → fullName   (the only remaining cross-service call)
     ↓
  EmergencyContactsRepository.findAllByUserId()
     ↓
  Publish Kafka audit event (fire-and-forget)
     ↓
  Build DTO → Cache in Redis (TTL 10 min) → Return
```

> Only cache **misses** currently publish the Kafka audit event — a cache hit is still a real access to someone's emergency data and arguably should be logged too (flagged in Next Phase below, not yet fixed).

**Update flow:** `PUT /api/profile → Save to PostgreSQL → cacheService.evictEmergencyProfile(publicProfileId) → Return`. No Feign call needed to evict — Medical Service has always had everything it needs, once ownership was corrected.

### Final Ownership

| Service | Owns | Exposes |
|---|---|---|
| Auth Service | `User`: id, fullName, email, password, createdAt | `GET /api/auth/internal/users/{userId}` → `UserBasicResponseDTO` |
| Medical Service | `MedicalProfile` (userId, **publicProfileId**, medical data), EmergencyContacts, Redis Cache, Emergency API | Emergency Profile endpoints |

### Design Principles Applied

Cache-Aside Pattern · Single Responsibility Principle · Bounded Context · Separation of Concerns · Source of Truth (Auth owns `fullName`; PostgreSQL owns everything behind Redis)

### Key Learnings

- Implementing Redis itself is easy — deciding **what** to cache and **who owns** the cached data is the actual architectural work.
- A cache key should belong to the service that owns the resource it identifies. If a service can't compute its own cache key without calling another service, that's a sign the identifier is owned by the wrong service.
- Redis didn't introduce the `publicProfileId` ownership flaw — it made the cost of ignoring it (an unresolvable cache eviction) immediate instead of latent.
- Not every duplicated-looking field is a bug: `fullName` staying in Auth while `publicProfileId` moves to Medical are different decisions because they're different kinds of data — identity vs. domain-specific identifier. The DDD bounded-context question ("which context does this concept belong to?") is the real test, not "which service currently has a Feign client for it."
- Moving an identifier's ownership can shrink a service's blast radius from a downstream failure — Auth being down no longer breaks emergency lookups entirely, only the `fullName` field within them.

### Next Phase (Not Yet Done)

- **Cache-hit audit logging** — only cache misses currently publish a Kafka audit event.
- **Circuit breaker for the Auth Feign call** — replace the manual `try/catch (RetryableException)` with a Resilience4j circuit breaker + fallback.
- **Backfill migration** — existing `medical_profile` rows created before this migration may have a `NULL publicProfileId`; needs a data migration before this reaches an environment with real data (`ddl-auto=update` can't add the `NOT NULL` constraint until that's resolved).
- **TTL tuning** — 10 minutes was a starting value; revisit once there's real access-pattern data.
- **Typed `RedisTemplate`** — currently `RedisTemplate<String, Object>`, with the cast to `EmergencyProfileResponseDTO` happening at the call site in `EmergencyProfileCacheService`. A `RedisTemplate<String, EmergencyProfileResponseDTO>` would remove that cast and be more explicit about what this cache actually stores — discussed, not yet done.

---

## ⚙️ Config Server — Centralized Configuration (Day 7)

### The Problem

Before Config Server, every service carried a full local `application.properties` — database config, Eureka URL, Kafka config, Redis config, JWT secret, server port, logging config — duplicated service to service. Fine at three services; doesn't scale, and every environment (dev/QA/prod) would multiply that duplication further.

**Before:**
```
Client → API Gateway → { Auth Service, Medical Service, Audit Service }
  Each service: application.yml (full local config)
```

**After:**
```
Git Repository (medinfo-config) → Config Server (8888) → { Auth, Medical, Audit, Gateway, Eureka }
```

### New Microservice: `config-server`

Dependencies: Spring Web, Spring Boot Actuator, Spring Cloud Config Server.

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```
`@EnableConfigServer` converts an ordinary Spring Boot app into a centralized configuration server — everything else about it (dependencies, structure) looks like any other service.

**Config Server's own config:**
```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/Naren-18/medinfo-config.git
          default-label: main
```
`default-label` pins which Git branch configuration is served from — the hook that would let a `dev` or `prod` branch serve different config later without touching any service.

### The Config Git Repository

A dedicated repo, `medinfo-config`, containing only YAML — no Java code:
```
medinfo-config
├── auth-service.yml
├── medical-service.yml
├── audit-service.yml
├── gateway-service.yml
└── eureka-server.yml
```
Each filename matches the corresponding service's `spring.application.name` exactly — that naming convention is how Config Server knows which file to serve to which caller.

### What Changed Locally in Each Service

**Before** — e.g. Auth Service's local `application.properties` held everything: datasource, JWT, Kafka, Redis, port, Eureka URL.

**After**, each service's local file shrinks to two lines:
```properties
spring.application.name=auth-service
spring.config.import=configserver:http://localhost:8888
```
Everything else — datasource, JPA, JWT, Eureka — moved into that service's file in the `medinfo-config` repo. `spring.application.name` is the lookup key; `spring.config.import` is where to look.

**Example — `auth-service.yml` in the config repo:**
```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://...
    username: ...
    password: ...
  jpa:
    hibernate:
      ddl-auto: update

jwt:
  secret: ...

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```
Same shape repeats for `medical-service.yml`, `audit-service.yml`, `gateway-service.yml`, `eureka-server.yml` — each service's config, just relocated.

### Config Loading Flow

```
Microservice starts → reads local application.properties → spring.application.name = auth-service
  → calls http://localhost:8888/auth-service/default
  → Config Server reads auth-service.yml from the Git repo
  → returns the YAML as the service's configuration
  → application starts normally, using that config
```
Every service follows the identical flow — the `{application-name}/default` URL pattern is Config Server's convention, resolved automatically from `spring.application.name`.

### Startup Order — Now a Hard Dependency

```
1. Config Server → 2. Eureka Server → 3. Auth Service → 4. Medical Service → 5. Audit Service → 6. API Gateway
```
Config Server has to be first and available before anything else, since every other service now depends on it just to read its own port and datasource on startup — a dependency that didn't exist before this change.

### Advantages

| Advantage | Why it matters |
|---|---|
| Centralized configuration | Single source of truth instead of N copies |
| Easier maintenance | Update one YAML file instead of hunting through every service |
| Environment management | Natural foundation for separate dev/QA/prod config later (Git branches or profile-specific files) |
| No rebuild required | Configuration changes don't require touching application code |
| Scalability | Adding a service means adding one config file, not copy-pasting boilerplate |

### Design Principles Applied

Separation of configuration from application code · Single source of truth (Git) · Convention over configuration (`{application-name}/default` lookup) · Explicit startup-order dependency management

### Key Learnings

- Configuration duplication is invisible at 2–3 services and becomes a real maintenance cost as the service count grows — Config Server is the standard fix, not a premature abstraction.
- Moving config out of each service doesn't remove the dependency, it relocates it: every service now depends on Config Server being up at startup, a new hard ordering constraint that didn't exist before.
- Naming convention (`{service-name}.yml` matching `spring.application.name`) is what makes the lookup automatic — get that naming wrong and a service silently gets no config, not an error pointing at the mismatch.
- Git as the config store is a deliberate choice: version history, code review on config changes, and branch-per-environment all come for free from a tool already in the workflow, rather than needing separate config-management tooling.

### Next Phase (Not Yet Done)

- **Config Server high availability** — currently a single instance; a Config Server outage blocks every other service from starting (though already-running services keep running on their last-fetched config).
- **Encrypted secrets** — JWT secret and DB credentials currently sit in plaintext YAML in the Git repo; Spring Cloud Config supports encryption at rest for exactly this.
- **Profile-specific config** (`auth-service-dev.yml`, `auth-service-prod.yml`) — the natural next step toward real environment separation, not yet built.
- **Config refresh without restart** (`/actuator/refresh` + Spring Cloud Bus) — currently a config change still requires restarting the consuming service to pick it up.

---

## 🧪 Testing (Day 4)

All three business services carry **JUnit 5 + Mockito** unit test suites with **JaCoCo** coverage reporting.

### Testing Architecture

```
                      JUnit 5
                         │
                  Mockito Extension
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   Mock Repository   Mock Feign      Mock Security
        │                │                │
        └────────────────┼────────────────┘
                         │
                  Service Under Test
                         │
                  Business Logic Only
```

**No PostgreSQL · No HTTP Requests · No Spring Boot Server · No Eureka · No API Gateway** — only business logic execution. Tests run in milliseconds, completely isolated from infrastructure.

### auth-service Tests

**AuthServiceTest** — business scenarios:
- Successful User Registration
- Duplicate Email Registration
- Successful Login
- Invalid Email Login
- Invalid Password Login
- Retrieve User using Public Profile ID
- User Not Found

Repository interactions, password encoding and JWT generation mocked with Mockito.

```
Register Request → existsByEmail() → PasswordEncoder → save() → Registration Success
```

**JWTServiceTest** — tested independently without Spring Security:
- JWT Token Generation
- Extract Username / Extract User ID / Extract Role
- Validate Token
- Invalid Username
- Expired Token

> 💡 **Reflection** was used to inject `secretKey` and `jwtExpiration` — these are normally injected via `@Value`, which doesn't run in a plain unit test.

**CustomUserDetailsServiceTest:**
- User Exists
- User Not Found

### medical-service Tests

**MedicalProfileServiceTest:**
- Create Medical Profile
- Duplicate Profile
- Update Profile
- Retrieve Profile
- Delete Profile
- Profile Not Found

Since the service reads the authenticated user from `SecurityContextHolder`, a **mocked SecurityContext** was built for every test:
```
Mock Authentication → Mock SecurityContext → SecurityContextHolder → Service
```

**EmergencyContactsServiceTest:**
- Create Contact
- Retrieve Contacts
- Empty Contact List
- Update Contact
- Delete Contact
- Contact Not Found
- Unauthorized Update
- Unauthorized Delete

**Ownership validation verified by testing contacts belonging to different users.**

**EmergencyServiceTest** — the service talks to multiple microservices; dependencies mocked:
- Authentication Service (Feign)
- Medical Repository
- Emergency Contact Repository

Scenarios:
- Successful Emergency Profile Retrieval
- Empty Emergency Contact List
- Authentication Service Unavailable
- Medical Profile Missing
- Audit Logging Verification

Feign communication completely mocked — service-to-service interactions tested **without real HTTP requests**:
```
Medical Service → Mock Auth Client  → User DTO
```

### audit-service Tests

**AuditServiceTest:**
- Successful Audit Log Creation
- Repository Failure

### Success and Failure Paths

| Success Scenarios | Failure Scenarios |
|---|---|
| Resource Creation | Duplicate Resources |
| Resource Retrieval | Missing Resources |
| Resource Update | Invalid Credentials |
| Resource Deletion | Unauthorized Access |
| Authentication Success | Downstream Service Failure |
| Emergency Profile Retrieval | Repository Exceptions |

Testing both execution paths significantly improves confidence in business logic.

### Code Coverage — JaCoCo

The JaCoCo Maven Plugin was added to Authentication Service, Medical Service, and Audit Service.

```bash
mvn clean test
```
automatically: executes all unit tests → collects execution data → generates an HTML coverage report at:
```
target/site/jacoco/index.html
```

The report highlights covered classes, methods, lines, branches — and uncovered code that needs additional test cases.

---

## 🌐 gateway-service

Status: ✅ **Complete** — routing all client traffic.

### What it does
- **Single public entry point** — clients only ever call port 8080
- Routes requests to downstream services by **URL pattern**
- Resolves targets dynamically via **Eureka + LoadBalancer** (`lb://` URIs)
- Foundation for future gateway-level JWT validation, centralized CORS, and rate limiting

### Project Setup

```
Group        : com.medinfo
Artifact     : gateway-service
Java         : 21
Spring Boot  : 3.5.x
```

**Dependencies:**
- Reactive Gateway (Spring Cloud Gateway — built on WebFlux)
- Eureka Discovery Client
- Spring Cloud BOM (`2025.0.0`) for version alignment

### Configuration

> **Day 7:** the block below now lives in `gateway-service.yml` in the `medinfo-config` Git repo. The local `application.properties` shrank to just `spring.application.name=gateway-service` + `spring.config.import=configserver:http://localhost:8888`.

```properties
spring.application.name=gateway-service
server.port=8080

eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true

# --- Routes ---
# Auth
spring.cloud.gateway.server.webflux.routes[0].id=auth-service
spring.cloud.gateway.server.webflux.routes[0].uri=lb://AUTH-SERVICE
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/auth/**,/api/users/**

# Medical
spring.cloud.gateway.server.webflux.routes[1].id=medical-service
spring.cloud.gateway.server.webflux.routes[1].uri=lb://MEDICAL-SERVICE
spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/profile/**,/api/contacts/**,/api/emergency/**
```

### Route Table

| Path Predicates | Target |
|---|---|
| `/api/auth/**`, `/api/users/**` | `lb://AUTH-SERVICE` |
| `/api/profile/**`, `/api/contacts/**`, `/api/emergency/**` | `lb://MEDICAL-SERVICE` |

> 💡 The `lb://` prefix tells Spring Cloud Gateway to use the LoadBalancer + Eureka to discover the destination dynamically — no hardcoded hosts or ports.

> ℹ️ The Audit Service has no Gateway route — it consumes Kafka events only; it no longer exposes any REST API.

### ⚠️ Real Issue Hit — UnknownHostException

Gateway requests initially failed because Eureka registered services under the machine's **corporate hostname** (`HSC-XXXX.allegisgroup.com`), which couldn't be resolved locally. Fix: set `eureka.instance.prefer-ip-address=true` on every service so Eureka registers IP addresses instead of hostnames.

### ⚠️ Real Issue Hit — Route Never Matched `/api/profile` (found during Day 6 verification)

`MedicalProfileController` is mapped at `/api/profile`, but the route predicate above originally only matched `/api/medical/**` — so profile CRUD 404'd through the Gateway despite working when hit directly on port 8082. Fixed by changing the predicate to `/api/profile/**` (reflected in the config above).

---

## 🧭 eureka-server

Status: ✅ **Complete** — all four services registered.

### What it does
- Central **Service Registry** — every microservice registers itself at startup
- Stores service name, host, port, status, and health information
- Receives periodic **heartbeats** from registered services
- Answers discovery queries: *"Where is AUTH-SERVICE?"* → current address
- Dashboard at `http://localhost:8761` — shows `AUTH-SERVICE`, `MEDICAL-SERVICE`, `AUDIT-SERVICE`, `GATEWAY-SERVICE` all UP

### Setup

```java
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {
}
```

**Dependencies:**
- Spring Cloud Netflix Eureka Server

### Configuration

> **Day 7:** now sourced from `eureka-server.yml` in `medinfo-config`; local file is just `spring.application.name` + `spring.config.import`.

```properties
spring.application.name=eureka-server
server.port=8761

# The server itself is not a client
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

> ℹ️ **Self Preservation Mode:** In local development the dashboard may show an "EMERGENCY!" warning. This is expected — Eureka avoids evicting instances when heartbeat traffic is low. In production with many services this disappears automatically.

---

## 🗂️ config-server

Status: ✅ **Complete** — new in Day 7, first service in the startup order.

### What it does

Serves every other service's configuration from a dedicated Git repository (`medinfo-config`), centralizing almost all runtime configuration that used to be duplicated in each service's local `application.properties`. Not literally all of it — each service still keeps `spring.application.name` and `spring.config.import` locally, by design (that's how it knows what to fetch and from where). See **⚙️ Config Server — Centralized Configuration (Day 7)** above for the full design reasoning.

### Project Setup

```
Project      : Maven
Language     : Java
Spring Boot  : 3.5.x
Java         : 21
Group        : com.medinfo
Artifact     : config-server
Package      : com.medinfo.configserver
```

**Dependencies:**
- Spring Web
- Spring Boot Actuator
- Spring Cloud Config Server

### Configuration

```properties
server.port=8888

spring.application.name=config-server
spring.cloud.config.server.git.uri=https://github.com/Naren-18/medinfo-config.git
spring.cloud.config.server.git.default-label=main
```

This is the one service whose configuration genuinely can't live in `medinfo-config` itself — it's what reads that repo, so it has to be self-contained.

---

## 🔐 auth-service

Status: ✅ **Complete** — fully independent, registered with Eureka, reachable via Gateway, unit tested.

### Structure

```
auth-service
├── config
│      SecurityConfig.java ✅
│
├── controller
│      AuthController.java ✅   (includes internal user-by-id endpoint — Day 6)
│
├── dto
│      LoginRequestDTO.java ✅
│      RegisterRequestDTO.java ✅
│      UserBasicResponseDTO.java ✅   (replaced UserPublicResponseDTO — Day 6)
│
├── entity
│      User.java ✅   (publicProfileId removed — moved to medical-service, Day 6)
│
├── exception
│      GlobalExceptionHandler.java ✅
│      ResourceNotFoundException.java ✅
│      ResourceAlreadyExistsException.java ✅
│      UnauthorizedException.java ✅
│      ServiceUnavailableException.java ✅
│      ErrorResponse.java ✅
│
├── repository
│      UserRepository.java ✅
│
├── security
│      JWTAuthenticationFilter.java ✅
│      JWTService.java ✅
│      CustomUserDetailsService.java ✅
│
├── service
│      AuthService.java ✅
│
├── test
│      AuthServiceTest.java ✅
│      JWTServiceTest.java ✅
│      CustomUserDetailsServiceTest.java ✅
│
└── AuthServiceApplication.java ✅
```

### Responsibilities
- User registration, login with BCrypt password verification
- JWT generation — includes **custom claims** (`userId`, `role`) so downstream services authenticate without a database lookup
- JWT validation via `JWTAuthenticationFilter` (runs on every request)
- `CustomUserDetailsService` — loads user from DB for Spring Security
- **Minimal internal user API** — `GET /api/auth/internal/users/{userId}` returns `userId` + `fullName` only, for Medical Service to resolve the one identity field it doesn't own (Day 6 — replaced the old `publicProfileId`-keyed public lookup, since Auth Service no longer knows about `publicProfileId` at all)
- Identity is now Auth Service's **only** concern — `publicProfileId` (an Emergency Profile concept) was moved out to Medical Service, Day 6
- Centralized exception handling with custom exceptions and `ErrorResponse` model
- **Eureka Client** — registers as `AUTH-SERVICE` and sends heartbeats
- **Fully unit tested** — registration, login, JWT lifecycle, internal user lookup

### Project Setup

```
Project      : Maven
Language     : Java
Spring Boot  : 3.5.x
Java         : 21
Group        : com.medinfo
Artifact     : auth-service
Package      : com.medinfo.auth
```

**Dependencies:**
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- Spring Cloud Netflix Eureka Client
- Spring Boot Test + Mockito

### Configuration

> **Day 7:** local `application.properties` is now just:
> ```properties
> spring.application.name=auth-service
> spring.config.import=configserver:http://localhost:8888
> ```
> Everything below now lives in `auth-service.yml` in the `medinfo-config` Git repo, fetched from Config Server at startup.

```properties
spring.application.name=auth-service

server.port=8081

spring.datasource.url=jdbc:postgresql://<host>/auth_db
spring.datasource.username=...
spring.datasource.password=...

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

jwt.secret=...
jwt.expiration=900000

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true
```

⚠️ Never commit real credentials to Git. Use environment variables in production — doubly true now that this file lives in a Git repo by design (see Config Server's Next Phase: encrypted secrets, not yet implemented).

### APIs (via Gateway — port 8080)

**Register User**
```
POST /api/auth/register
```
```json
{
  "fullName": "Narendra Kumar",
  "email": "narendra@gmail.com",
  "password": "password123"
}
```

**Login User**
```
POST /api/auth/login
```
```json
{
  "email": "narendra@gmail.com",
  "password": "password123"
}
```
Response:
```json
{
  "success": true,
  "message": "Login successful",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

JWT payload contains custom claims:
```json
{
  "sub": "admin@gmail.com",
  "userId": 1,
  "role": "USER"
}
```

**Get User By ID** *(inter-service use only — Day 6, replaced the old public-profile-based lookup)*
```
GET /api/auth/internal/users/{userId}
```
Response:
```json
{
  "userId": 1,
  "fullName": "Narendra Kumar"
}
```
`permitAll()` — no JWT required. This matches how the old lookup actually behaved in practice (Feign calls never carried a token); the endpoint is simply not publicly advertised.

---

## 🩺 medical-service

Status: ✅ **Complete** — pure medical domain, audit via Kafka events, unit tested.

### Structure

```
medical-service
├── cache
│      EmergencyProfileCacheService.java ✅   ← Day 6
│      │
├── client
│      AuthClient.java ✅
│      │
├── config
│      SecurityConfig.java ✅
│      FeignConfig.java ✅
│      RedisConfig.java ✅   ← Day 6
│
├── controller
│      EmergencyController.java ✅
│      EmergencyContactsController.java ✅
│      MedicalProfileController.java ✅
│
├── dto
│      CreateMedicalProfileDTO.java ✅   (redundant fullName field removed — Day 6)
│      MedicalProfileResponseDTO.java ✅
│      EmergencyProfileResponseDTO.java ✅   (@NoArgsConstructor added — Day 6)
│      EContactsDTO.java ✅   (@NoArgsConstructor added — Day 6)
│      UserBasicResponseDTO.java ✅   (replaced UserPublicResponseDTO — Day 6)
│      │
├── entity
│      MedicalProfile.java ✅   (now owns publicProfileId — moved from Auth Service, Day 6; redundant fullname field removed)
│      EmergencyContacts.java ✅
│
├── exception
│      GlobalExceptionHandler.java ✅
│      ResourceNotFoundException.java ✅
│      ResourceAlreadyExistsException.java ✅
│      UnauthorizedException.java ✅
│      ServiceUnavailableException.java ✅
│      CustomFeignErrorDecoder.java ✅
│      ErrorResponse.java ✅
│
├── repository
│      MedicalProfileRepository.java ✅
│      EmergencyContactsRepository.java ✅
│
├── security
│      JWTAuthenticationFilter.java ✅
│      JWTService.java ✅
│
├── service
│      MedicalProfileService.java ✅
│      EmergencyContactsService.java ✅
│      EmergencyService.java ✅
│
├── test
│      MedicalProfileServiceTest.java ✅
│      EmergencyContactsServiceTest.java ✅
│      EmergencyServiceTest.java ✅
│
└── MedicalServiceApplication.java ✅
```

### Responsibilities
- Medical Profile CRUD — now owns and generates `publicProfileId` (`UUID.randomUUID()`) at profile creation (Day 6)
- Emergency Contacts CRUD
- Public Emergency Profile API — resolves `publicProfileId` → `MedicalProfile` locally (own database, own identifier, Day 6), then Feign to Auth Service only for `fullName`
- **Redis cache (Cache-Aside)** — `EmergencyProfileCacheService` fronts the Emergency Profile API; 10-min TTL as a safety net alongside explicit eviction on update (Day 6)
- **Kafka producer** — publishes `AuditLogEvent` to `emergency-access-events` on every emergency access **cache miss**, keyed by userId (Day 5)
- **JWT validation only** — does not generate tokens, uses the same signing secret as Auth Service
- **No direct access to other services' databases** — `userId` references + Feign/events only
- **One OpenFeign client** — `AuthClient`, narrowed to `fullName` resolution only (Day 6; previously resolved the whole user)
- **Centralized exception framework** with custom exceptions, `ErrorResponse`, and `CustomFeignErrorDecoder`
- **Eureka Client** — registers as `MEDICAL-SERVICE`
- **Fully unit tested** — CRUD paths, ownership validation across users, mocked Feign clients including downstream-unavailable, mocked SecurityContext, mocked `EmergencyProfileCacheService`

### Project Setup

```
Project      : Maven
Language     : Java
Spring Boot  : 3.5.x
Java         : 21
Group        : com.medinfo
Artifact     : medical-service
Package      : com.medinfo.medical
```

**Dependencies:**
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Validation
- Lombok
- Spring Cloud OpenFeign (`spring-cloud-starter-openfeign`)
- Spring Cloud Netflix Eureka Client
- Spring Kafka (`spring-kafka`) ← Day 5
- Spring Boot Starter Data Redis (`spring-boot-starter-data-redis`) ← Day 6
- Spring Boot Test + Mockito

**Database:** `medical_db` · **Port:** `8082` · **Cache:** Redis, `localhost:6379`

### Configuration

> **Day 7:** local `application.properties` is now just:
> ```properties
> spring.application.name=medical-service
> spring.config.import=configserver:http://localhost:8888
> ```
> Everything below now lives in `medical-service.yml` in the `medinfo-config` Git repo.

```properties
spring.application.name=medical-service

server.port=8082

spring.datasource.url=jdbc:postgresql://<host>/medical_db
spring.datasource.username=...
spring.datasource.password=...

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Same signing secret as auth-service — required for JWT signature verification
jwt.secret=...
jwt.expiration=900000

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true

# Kafka Producer (Day 5)
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Redis (Day 6)
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Emergency Profile Flow (Gateway + Eureka + Redis + Feign + Kafka) — Day 6

```
Client
↓
API Gateway (8080) → route match /api/emergency/**
↓
Eureka → MEDICAL-SERVICE
↓
Redis GET (emergency-profile::<publicProfileId>)
↓
Cache HIT? ──Yes──► Return cached EmergencyProfileResponseDTO immediately (no DB, no Feign, no Kafka)
↓ No
MedicalProfileRepository.findByPublicProfileId()   ← Medical Service resolves its own resource, no Feign needed (Day 6)
↓
MedicalProfile → userId
↓
AuthClient (OpenFeign) → Eureka → AUTH-SERVICE   ← only remaining cross-service call, fullName only
↓
UserBasicResponseDTO { userId, fullName }
↓
EmergencyContactsRepository.findAllByUserId()
↓
event.eventId = UUID.randomUUID()   ← unique identity for idempotent consumption
↓
kafkaTemplate.send("emergency-access-events", userId, event)   ← fire and forget (Day 5)
↓
Build EmergencyProfileResponseDTO → cacheService.cacheEmergencyProfile() (TTL 10 min)
↓
Return to Client

              (asynchronously, at its own pace)
Kafka topic → Audit Service @KafkaListener → audit_db
```

Medical Service touches only `medical_db` (and Redis) — `fullName` is the only field still sourced from Auth Service; audit events flow through Kafka on cache misses.

**Update flow:**
```
PUT /api/profile → MedicalProfileService.updateProfile() → Save to medical_db
↓
cacheService.evictEmergencyProfile(medicalProfile.getPublicProfileId())   ← no Feign call needed, Day 6
↓
Return
```

### Key Architectural Changes

**Domain Model — replaced JPA User relationship with userId:**
```java
// Before (Monolith)
@ManyToOne
@JoinColumn(name = "user_id")
private User user;

// After (Microservices)
@Column(nullable = false)
private Long userId;
```

**JWT Authentication — no database lookup:**
```
JWT → Validate Signature → Extract userId → SecurityContextHolder
```

**Audit — from local, to Feign, to Kafka:**
```
// Day 3: local persistence
Emergency Profile Viewed → EmergencyAccessLogService → medical_db

// Day 5: asynchronous event
Emergency Profile Viewed → KafkaTemplate → emergency-access-events → Audit Service → audit_db
```

**Identifier ownership — `publicProfileId` moved from Auth Service to Medical Service (Day 6):**
```java
// Before — auth-service.User
private String publicProfileId;   // Medical Service had to call Auth to get its own resource's key

// After — medical-service.MedicalProfile
@Column(unique = true, nullable = false)
private String publicProfileId;   // generated locally: UUID.randomUUID().toString()
```
Root cause was cache eviction: `updateProfile()` only had `userId`, not `publicProfileId`, so it couldn't invalidate its own Redis key without a cross-service call — a strong signal the identifier was owned by the wrong service. `fullName` deliberately stayed in Auth Service (identity data, not an Emergency Profile concept) — see the Day 6 section above for the full reasoning.

**Exception Handling — custom exceptions + Feign Error Decoder:**

| HTTP Status | Exception |
|---|---|
| 404 | ResourceNotFoundException |
| 401 | UnauthorizedException |
| 409 | ResourceAlreadyExistsException |
| 503 | ServiceUnavailableException |
| 500 | Generic handler |

`CustomFeignErrorDecoder` maps HTTP errors from downstream services into the correct custom exceptions. Connection failures (no HTTP response) are caught at the service level and mapped to `ServiceUnavailableException` → 503.

---

## 🧾 audit-service

Status: ✅ **Complete** — pure Kafka consumer, single source of truth for auditing, unit tested.

### What it does
- **Consumes `AuditLogEvent`** from the `emergency-access-events` topic (consumer group: `audit-group`)
- Persists every emergency profile access: who, from where, with what client, how, and when (true access time from the event)
- Designed **generically** (`AuditLog`, not `EmergencyAccessLog`) so future events — user login, profile updates, contact modifications, password changes — land in the same service
- **No REST API at all** — the `POST /api/audit/log` endpoint was removed on Day 5; events are the only entry point
- **Owns both Kafka topics** — `emergency-access-events` and `emergency-access-events.DLT` — created automatically via `KafkaTopicConfig`
- **Bounded retry** — `DefaultErrorHandler` + `FixedBackOff` (3 attempts, 1s interval) before giving up on a record
- **Dead Letter Topic** — `DeadLetterPublishingRecoverer` publishes unprocessable events to `emergency-access-events.DLT` instead of discarding them, preserving the original partition
- **Also a Kafka producer** — a `KafkaProducerConfig` was added solely to support publishing to the DLT
- **Idempotent consumer** — unique `eventId` (UUID) per event, `UNIQUE` DB constraint, `existsByEventId()` check before every insert, so Kafka's at-least-once redelivery never creates duplicate rows
- **Internal-only service**: no Spring Security, no Gateway route
- **Unit tested** — successful audit log creation + repository failure

### Project Setup

```
Group        : com.medinfo
Artifact     : audit-service
Java         : 21
Spring Boot  : 3.5.x
```

**Dependencies:**
- Spring Web
- Spring Data JPA
- Validation
- PostgreSQL Driver
- Lombok
- Eureka Discovery Client
- Spring Kafka (`spring-kafka`) — Day 5 consumer, Day 5-reliability producer (for DLT publishing)
- Spring Boot Test

> 💡 Spring Security intentionally **not** added — this is an internal microservice.

**Database:** `audit_db` · **Port:** `8083`

### Configuration

> **Day 7:** local `application.properties` is now just:
> ```properties
> spring.application.name=audit-service
> spring.config.import=configserver:http://localhost:8888
> ```
> Everything below now lives in `audit-service.yml` in the `medinfo-config` Git repo.

```properties
spring.application.name=audit-service
server.port=8083

eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true

# Kafka Consumer (Day 5)
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=audit-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.medinfo.common.events

# Kafka Producer — reliability only, used by DeadLetterPublishingRecoverer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
```

### Domain — AuditLog

| Field | Purpose |
|---|---|
| id | Primary key |
| eventId | Globally unique event UUID — `UNIQUE, NOT NULL` — enables idempotent consumption |
| userId | Which user's data was accessed |
| ipAddress | Where the request came from |
| userAgent | What client made the request |
| accessMethod | `URL` or `QR_CODE` (enum) |

### Consumer

```java
@KafkaListener(
    topics = "emergency-access-events",
    groupId = "audit-group"
)
public void consume(AuditLogEvent event) {
    auditService.saveAuditLog(event);
}
```

`AuditService.saveAuditLog()` performs the idempotency check before persisting:

```java
public void saveAuditLog(AuditLogEvent event) {
    if (auditRepository.existsByEventId(event.getEventId())) {
        log.info("Duplicate event ignored: {}", event.getEventId());
        return;
    }
    auditRepository.save(toEntity(event));
}
```

Flow:

```
Kafka Topic

↓

AuditEventConsumer

↓

AuditService → existsByEventId()  →  duplicate? → ignore

↓ (new event)

AuditRepository

↓

audit_db
```

> See **🛡️ Kafka Reliability** above for the full retry + Dead Letter Topic + idempotency implementation and testing.


---

## 🧠 Principles Learned

- **Migrating to microservices is not just moving Java classes.** Each service needs its own source code, dependencies, configuration, database, security setup, and `pom.xml`.
- **Migrate bottom-up:** DTO → Entity → Repository → Service → Controller → Security → Exception. This order minimizes compilation errors.
- **Cross-service JPA relationships are impossible.** Replace with a plain `userId` reference — never duplicate entities, never cross-query databases.
- **Shared JWT secret enables decentralized authentication.** Every service verifies tokens independently — no token-introspection call to Auth Service.
- **Custom JWT claims avoid unnecessary database calls.** `userId` and `role` embedded in the token mean downstream services can authenticate with zero DB lookups.
- **Each service owns its data. Others access it through APIs, never through the database.**
- **Feign Error Decoder only handles HTTP responses.** Connection failures produce a `RetryableException`, not an HTTP response — handle both separately. Long-term solution: Resilience4j Circuit Breakers.
- **Hardcoded service URLs don't survive real environments.** Service Discovery lets consumers resolve providers by logical name, with zero code changes when locations change.
- **Service Discovery solves service-to-service coupling; the API Gateway solves client-to-service coupling.** Eureka frees services from knowing each other's addresses, the Gateway frees clients from knowing any service's address.
- **Eureka may register hostnames your network can't resolve.** Corporate machine hostnames caused `UnknownHostException` at the Gateway — `eureka.instance.prefer-ip-address=true` forces IP registration.
- **A capability that isn't part of your domain belongs in its own service.** Audit logging worked inside Medical Service but violated bounded context — extraction gave it its own database, its own scaling, and made it reusable for future event types.
- **Design extracted domains generically.** Renaming `EmergencyAccessLog` → `AuditLog` turned a single-purpose table into a platform capability.
- **Internal services don't automatically need Spring Security.** The Audit Service receives traffic only from backend services — deliberate omission, not oversight.
- **Unit tests isolate business logic from infrastructure.** Mock the repository, mock the Feign clients, mock the SecurityContext — tests run in milliseconds.
- **Test the failure paths, not just the happy path.** Failures are where production bugs live.
- **`@Value` fields need reflection in plain unit tests.** Spring's property injection doesn't run without a Spring context.
- **Coverage reports show what you *haven't* tested.** JaCoCo turns "I think it's tested" into "I know what's missing."
- **Sync vs async is decided by one question: does the response depend on the result?** User resolution → yes → Feign. Audit logging → no → Kafka. (Day 5)
- Shared event contracts simplify communication between services.
- AuditLogEvent lives inside medinfo-common and is shared between producer and consumer.
- Kafka enables asynchronous communication, reducing service coupling and improving system resilience.
- Producer publishes events without knowing which services consume them.
- Consumers independently process events from Kafka topics.
- Consumer Groups and Offsets ensure reliable message consumption.
- **A working consumer isn't a reliable consumer.** Retry, Dead Letter Topic, and idempotency are three separate problems — transient failure, poison messages, and at-least-once redelivery — and need three separate fixes, not one generic try-catch.
- **The consumer owns retry and dead-letter responsibility, so it owns topic management too.** Moving `KafkaTopicConfig` from Medical Service to Audit Service kept ownership aligned with responsibility.
- **A Dead Letter Topic needs a producer.** Even a pure-consumer service becomes a producer the moment it needs to publish failed events elsewhere — this doesn't break the "Audit Service has no REST API" principle, since it's an internal Kafka-to-Kafka handoff, not a client-facing endpoint.
- **Kafka's at-least-once delivery is a guarantee, not an edge case.** Idempotency (`eventId` + unique constraint + `existsByEventId()`) has to be designed in from the start, not bolted on after a duplicate-row bug in production.
- **Implementing a cache is easy; deciding what to cache and who owns the cached data is the real work.** The Cache-Aside pattern itself was a day's work; the ownership question it exposed took longer to reason through than the Redis wiring did. (Day 6)
- **A cache key should belong to the service that owns the resource it identifies.** If a service can't compute or invalidate its own cache key without calling another service, that's a sign the identifier is owned by the wrong service — not just a caching inconvenience.
- **A feature can expose a pre-existing design flaw without causing it.** `publicProfileId` had been in the wrong bounded context since Day 1; Redis didn't create that problem, it just made ignoring it impossible once cache eviction needed the identifier.
- **Not every duplicated-looking field is a bug.** `publicProfileId` moved to Medical Service, `fullName` stayed in Auth Service — different decisions for different reasons (domain-specific identifier vs. identity data), decided by "which bounded context does this concept belong to," not "which service currently has a Feign client for it."
- **Fixing an ownership bug can shrink blast radius as a side effect.** After the redesign, Auth Service being down only costs the `fullName` field in the Emergency Profile response — not the entire lookup, as it did before.
- **End-to-end verification catches what unit and direct-service testing can't.** The Gateway's missing `/api/profile/**` route predicate was invisible until the full Gateway → Medical Service path was exercised for real.
- **Configuration duplication is invisible at 2–3 services and becomes a real cost as the count grows.** Config Server centralizes it, but doesn't remove the dependency — it relocates it: every service now needs Config Server up before it can read its own port. (Day 7)
- **Naming conventions can replace explicit wiring, at the cost of silent failure on typos.** `spring.application.name` matching `{name}.yml` in the config repo is what makes the Config Server lookup automatic — get the name wrong and a service just doesn't get its config, with no obvious pointer to why.
- **Externalizing configuration doesn't externalize the risk of committing secrets.** JWT secrets and DB credentials moved out of each service's local file, but into a Git repo — still plaintext, still a real gap until encryption at rest is added.

---

## ✅ Progress

- [x] Planned the microservices architecture
- [x] Designed clear service boundaries
- [x] Created independent Spring Boot projects per service
- [x] Configured separate PostgreSQL databases (`auth_db`, `medical_db`, `audit_db`)
- [x] Migrated the complete Authentication domain
- [x] Successfully launched auth-service independently
- [x] Created medical-service with its own independent database
- [x] Migrated the complete Medical domain
- [x] Redesigned domain model — replaced JPA `User` relationships with `userId` references
- [x] Redesigned JWT to include custom claims (`userId`, `role`)
- [x] Implemented independent JWT validation in Medical Service (shared signing secret)
- [x] Introduced Public User API in Auth Service
- [x] Implemented OpenFeign in Medical Service (`AuthClient`)
- [x] Implemented centralized exception framework (custom exceptions + `ErrorResponse`)
- [x] Introduced `CustomFeignErrorDecoder` via `FeignConfig`
- [x] Created Eureka Server (port 8761)
- [x] Registered all services as Eureka Clients
- [x] Removed hardcoded Feign URLs — services resolved by logical name via Eureka
- [x] Created API Gateway with Spring Cloud Gateway (Reactive)
- [x] Configured dynamic routing with `lb://` URIs and path predicates
- [x] Routed all client traffic through single entry point (8080)
- [x] Fixed hostname resolution (`eureka.instance.prefer-ip-address=true`)
- [x] Created Audit Service with dedicated `audit_db`
- [x] Designed generic `AuditLog` domain (URL / QR_CODE access methods)
- [x] Migrated audit logging from Medical Service to Audit Service
- [x] Removed `EmergencyAccessLog` entity, repository, and service from Medical Service
- [x] JUnit 5 + Mockito unit tests across all three business services
- [x] Mocked SecurityContextHolder, mocked Feign clients, `@Value` via reflection
- [x] Success and failure scenarios tested across all services
- [x] JaCoCo integrated with HTML coverage reports
- [x] Installed Apache Kafka using Docker Compose
- [x] Configured Kafka UI
- [x] Created KafkaTopicConfig
- [x] Created emergency-access-events topic
- [x] Introduced AuditLogEvent shared contract
- [x] Added Kafka Producer configuration
- [x] Added Kafka Consumer configuration
- [x] Implemented AuditEventProducer
- [x] Implemented AuditEventConsumer
- [x] Configured JsonSerializer
- [x] Configured JsonDeserializer
- [x] Configured Consumer Groups
- [x] Learned Kafka Offsets
- [x] Successfully migrated Audit communication from Feign to Kafka
- [x] Removed AuditClient
- [x] Removed AuditController
- [x] Removed CreateAuditLogRequestDTO
- [x] Verified end-to-end asynchronous event flow
- [x] Configured `DefaultErrorHandler` with `FixedBackOff` (3 attempts, 1s interval)
- [x] Tested bounded retry with an intentional consumer exception
- [x] Moved Kafka topic configuration from Medical Service to Audit Service
- [x] Created Dead Letter Topic (`emergency-access-events.DLT`)
- [x] Added Kafka producer capability to Audit Service (for DLT publishing)
- [x] Configured `DeadLetterPublishingRecoverer` with partition preservation
- [x] Verified failed events land in the DLT while the client still gets a successful response
- [x] Added unique `eventId` (UUID) to `AuditLogEvent`
- [x] Added unique DB constraint on `AuditLog.eventId`
- [x] Implemented `existsByEventId()` idempotency check
- [x] Tested duplicate delivery — verified exactly one row per event
- [x] Added Redis to `docker-compose.yml`
- [x] Configured Spring Boot Redis integration (`RedisConfig`, `RedisTemplate` with String/JSON serializers)
- [x] Implemented the Cache-Aside pattern in `EmergencyService`
- [x] Fixed JSON serialization by adding `@NoArgsConstructor` to `EmergencyProfileResponseDTO` and `EContactsDTO`
- [x] Refactored cache logic into a dedicated `EmergencyProfileCacheService` (SRP)
- [x] Implemented cache-key design (`emergency-profile::<publicProfileId>`), hit/miss logging, and 10-min TTL as a safety net
- [x] Implemented cache eviction on profile update
- [x] Identified the architectural flaw Redis exposed — Medical Service couldn't evict its own cache because it didn't own `publicProfileId`
- [x] Moved `publicProfileId` from `auth-service.User` to `medical-service.MedicalProfile`, generated via `UUID.randomUUID()`
- [x] Removed the old `GET /api/users/public/{publicProfileId}` endpoint and related methods from Auth Service
- [x] Rejected duplicating `fullName` into Medical Service — kept identity data in Auth Service
- [x] Removed the pre-existing redundant `fullname` field from `MedicalProfile` / `CreateMedicalProfileDTO`
- [x] Added a minimal internal Auth API — `GET /api/auth/internal/users/{userId}` → `UserBasicResponseDTO`
- [x] Re-added `ServiceUnavailableException` + 503 handler for Auth Feign failures
- [x] Fixed a Gateway routing bug (`/api/profile/**` was never routed) discovered while verifying the redesign end-to-end
- [x] Updated all affected unit tests (`AuthServiceTest`, `EmergencyServiceTest`, `MedicalProfileServiceTest`) to match the new ownership model
- [ ] Cache-hit audit logging (currently only cache misses publish a Kafka event)
- [ ] Circuit breaker (Resilience4j) for the Auth Feign call, replacing the manual try/catch
- [ ] `publicProfileId` backfill migration for pre-existing `medical_profile` rows
- [ ] TTL tuning based on real access-pattern data
- [ ] Typed `RedisTemplate<String, EmergencyProfileResponseDTO>` (currently `RedisTemplate<String, Object>` with a cast at the call site)
- [x] Created `config-server` Spring Boot application with `@EnableConfigServer`
- [x] Created dedicated `medinfo-config` Git repository (YAML only, no code)
- [x] Configured Config Server to read from Git (`spring.cloud.config.server.git.uri`, `default-label`)
- [x] Migrated all per-service configuration (`auth-service.yml`, `medical-service.yml`, `audit-service.yml`, `gateway-service.yml`, `eureka-server.yml`) into the config repo
- [x] Reduced every service's local `application.properties` to `spring.application.name` + `spring.config.import`
- [x] Verified the full config-loading flow end to end (`GET /{application-name}/default`) for every service
- [x] Established the new startup order (Config Server → Eureka → Auth → Medical → Audit → Gateway)
- [ ] Config Server high availability (currently a single instance)
- [ ] Encrypted secrets in the config repo (JWT secret, DB credentials currently plaintext)
- [ ] Profile-specific config (dev/qa/prod)
- [ ] Config refresh without restart (`/actuator/refresh` + Spring Cloud Bus)
- [ ] Docker & Docker Compose for the remaining services (Redis itself is already containerized)
- [ ] CI/CD with GitHub Actions
- [ ] Cloud Deployment

---

## 📅 Current Status

**Six applications + Kafka broker + Redis running, config centralized.** The architecture is now genuinely event-driven where it should be, synchronous where it must be, cached where traffic demands it, reliable where failures are inevitable, each service owns exactly the identifiers it needs, and configuration is a single Git-backed source of truth instead of duplicated per service:

```
Config Server (8888) → { Auth, Medical, Audit, Gateway, Eureka } — config fetched at startup
Client → Gateway → Eureka → { AUTH, MEDICAL }
Medical → Redis                                              (synchronous, in-process — cache-aside)
Medical → Feign → Auth  (fullName only)                       (synchronous — narrowed scope, Day 6)
Medical → Kafka → emergency-access-events → Audit             (asynchronous — fire and forget, on cache miss)
Audit  → Retry (3×) → DeadLetterPublishingRecoverer → emergency-access-events.DLT   (on failure)
Audit  → existsByEventId() → ignore | save                    (on redelivery)

mvn clean test → JaCoCo HTML report per service
```

The failure tests proved the design: Audit Service down → emergency response unaffected → events buffered and consumed on recovery. Poison message → 3 retries → DLT, partition unblocked, client response unaffected throughout. Duplicate delivery → exactly one audit record (idempotent consumer). Redis eviction on update → next read always fresh from PostgreSQL. Auth Service down → only the `fullName` field is affected, not the whole emergency lookup. Config Server verified end to end for every service's `GET /{application-name}/default` lookup.

The strongest story from Day 6 isn't the caching itself — it's that implementing cache eviction exposed a pre-existing ownership bug (`publicProfileId` living in Auth Service when Medical Service needed it to invalidate its own cache) that had been latent in the architecture since Day 1. The Day 7 story is the trade-off, stated plainly: centralizing configuration removes duplication, but makes Config Server itself a new hard startup dependency for every other service — worth naming unprompted, not just presenting as a strict win.

Next milestone: **Day 8 — Docker & Docker Compose** for the remaining services (Redis is already containerized), followed by CI/CD with GitHub Actions and cloud deployment 🚀