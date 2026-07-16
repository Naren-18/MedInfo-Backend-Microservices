# Backend Architecture — Revision Notes
### Generic implementation knowledge (not tied to MedInfo) — for interviews & real design decisions

> Note on scope: Topics 1–10 are things you've actually built (traced from your Notion docs).
> Topics 11–16 (Redis, Docker, Compose, CI/CD, Logging/Monitoring, Cloud Deployment) are on your
> "planned next" list — not yet built in MedInfo — so those sections are written as **generic
> real-world implementation knowledge** you should know even before building them, so you're not
> caught off guard if asked.

---

## 1. Authentication & JWT

**The problem it solves:** HTTP is stateless. After login, the server needs a way to know who's
making the next request without re-checking a password every time, and without storing
server-side session state (which doesn't scale horizontally).

**Core flow you must be able to draw on a whiteboard:**
```
Register → hash password (BCrypt) → store user
Login → verify password (BCrypt.matches) → generate JWT → return to client
Every subsequent request → client sends JWT in Authorization: Bearer header
Server → filter runs once per request → validates JWT → sets SecurityContext → request proceeds
```

**Key implementation pieces (Spring Security):**
- `PasswordEncoder` bean (BCrypt) — never store or compare plaintext passwords.
- `JWTService` — three real jobs: **generate** (sign a token with claims + expiry),
  **extract** (read claims back out), **validate** (check signature + expiry).
- `UserDetailsService` — Spring Security's contract for "given a username, give me a
  UserDetails object." You implement `loadUserByUsername()` to bridge your DB user to what
  Spring Security understands.
- `OncePerRequestFilter` subclass (`JWTAuthenticationFilter`) — runs on every request, reads
  the header, validates the token, and manually builds a
  `UsernamePasswordAuthenticationToken` and puts it into `SecurityContextHolder`. This is the
  single most important method to be able to explain end-to-end in an interview.
- `SecurityFilterChain` bean — declares which routes are public (`permitAll()`) vs need auth
  (`anyRequest().authenticated()`), sets session policy to `STATELESS`, and registers your JWT
  filter **before** `UsernamePasswordAuthenticationFilter`.

**JWT structure to know cold:** `Header.Payload.Signature`. Signature = 
`HMAC_SHA256(base64(header)+"."+base64(payload), secret)`. This is what makes tampering
detectable — change one character of the payload and the signature no longer matches.

**Real implementation gotchas that show up in every project:**
- Forgetting `return` after `filterChain.doFilter()` in the "no token, skip" branch →
  NullPointerException downstream.
- Two `SecurityConfig` classes / duplicate beans → "bean already exists" errors.
- Confusing your own `User` entity with `org.springframework.security.core.userdetails.User` —
  same class name, different package, causes ClassCastException.
- 401 vs 403: 401 = not authenticated (no/invalid token). 403 = authenticated but not
  authorized (valid token, insufficient permission).

**Stateless vs session-based, in one sentence you can say out loud:** sessions require the
server to remember who's logged in (doesn't scale across multiple instances without a shared
session store); JWT pushes that state into the token itself, so any server instance can
validate any request without shared memory.

---

## 2. Monolith → Microservices

**Why split at all — the real answer, not the buzzword answer:** you split when a single
codebase starts having independent scaling needs, independent deployment cadence, or clearly
separable business capabilities (bounded contexts) that different teams would own. Splitting
for its own sake adds network calls, partial failure, and data-consistency problems you didn't
have before — so the justification has to be real.

**Design before you code — decide these first:**
1. **Bounded contexts** — what does each service *own*? (e.g., an Auth context vs a Billing
   context vs an Orders context). A service should map to a business capability, not a table.
2. **Database-per-service** — the single most important rule. No service reaches into another
   service's tables. If Service A needs data owned by Service B, it asks B's API — never the DB
   directly. This is what actually forces you to design clean service boundaries.
3. **What's shared vs independent** — each service gets its own repo (or its own module in a
   monorepo), own `pom.xml`/dependencies, own config, own database, own deployment pipeline.

**Migration order that minimizes broken builds:**
```
Entity → Repository → DTO → Service → Controller → Security → Exception handling
```
Bottom-up, because Controllers depend on Services which depend on Repositories which depend on
Entities — migrating bottom-up means each layer compiles before you add the next.

**The part everyone underestimates — breaking cross-entity JPA relationships.**
In a monolith you'd naturally write:
```java
@ManyToOne
@JoinColumn(name = "user_id")
private User user;
```
After the split, the owning service (say Orders) no longer has access to the `User` entity or
table. The fix is a plain foreign-key-style field instead of a JPA relationship:
```java
@Column(nullable = false)
private Long userId;
```
If you need more than the ID (name, email, etc.), you fetch it from the owning service's API —
never join across databases. This single change (`@ManyToOne User user` → `Long userId`) is a
concrete, defensible interview story about reducing coupling.

**What "each service is independent" means concretely, not abstractly:** independent source
code, independent Maven/Gradle build, independent `application.properties`, independent
database, independent Spring Security config, independent deploy — a checklist, not a feeling.

---

## 3. JWT Redesign for Microservices

**The problem a monolith-style JWT creates:** if your JWT only contains `{"sub": "email"}`,
every downstream service that needs the user's ID has to query the Auth database to resolve
email → userId. But other services don't (and shouldn't) have access to the Auth database.

**The fix — embed what downstream services need as custom claims:**
```json
{ "sub": "user@email.com", "userId": 1, "role": "USER" }
```
Now any service can read `userId` straight out of the token — **zero database lookups** for
authorization. This is the actual architectural upgrade, not just "I added JWT."

**Split of responsibility across services:**
| Responsibility | Auth/Identity service | Every other service |
|---|---|---|
| Generate + sign JWT | ✅ | ❌ |
| Validate signature | ✅ | ✅ |
| Extract claims | ✅ | ✅ |
| DB lookup for auth | ✅ (only at login) | ❌ never |

**Shared secret is what makes this work.** Both services configure the same
`jwt.secret`. The issuing service signs with it; every other service verifies the signature
with the same key. If secrets differ, every request fails auth — this is the #1 thing to check
when JWT validation mysteriously fails after a service split.

**Redesigned validation flow (no DB call):**
```
JWT → validate signature → extract userId (and role) → build Authentication manually
    → SecurityContextHolder
```
```java
new UsernamePasswordAuthenticationToken(userId, null, authorities);
```
You don't need to load a full `UserDetails` object from a database anymore — you only need
the identifier to make authorization decisions ("is this userId allowed to touch this
resource?").

**Generic principle to state in an interview:** in microservices, JWT becomes the mechanism
that lets services trust a claim about identity without a runtime dependency on the service
that issued it. That's the whole point of moving auth data into the token.

---

## 4. Inter-Service Communication

Two flavors — know when to reach for each, this is a very common interview question.

**Synchronous (REST via Feign / WebClient / RestTemplate)** — use when the caller's response
*depends* on the callee's answer. Example: Order Service needs the current price from Product
Service before it can confirm an order — it cannot proceed without that answer.

**Asynchronous (message broker — Kafka/RabbitMQ)** — use when the caller doesn't need to wait
for the result to complete its own response. Example: "send a notification after payment
succeeds" — the payment API response shouldn't wait on whether the notification actually sent.

**Why not just call another service's database directly?** Because that recreates tight
coupling at the data layer — the exact thing microservices are supposed to remove. Every
cross-service data need should go through that service's public API contract (a DTO), never
its schema.

**OpenFeign — how it actually works, not just "it's a REST client":**
- You declare an interface annotated `@FeignClient(name = "service-name")` with method
  signatures that look like your own service methods but map to the other service's REST
  endpoints.
- Enable with `@EnableFeignClients` on the main application class.
- Without service discovery, you hardcode `url = "http://localhost:8081"` — brittle, breaks the
  moment that service moves.
- With service discovery (Eureka/Consul), you drop the URL entirely — Feign resolves the
  current address by service name at call time.

**Designing the API you expose to other services** — don't return your internal entity.
Return a dedicated response DTO with only what other services legitimately need (e.g. a
`UserPublicResponseDTO` with `userId` + `fullName`, not the full `User` with password hash and
internal fields). This is the same DTO principle as client-facing APIs, just applied
internally.

**Failure handling is the part people skip and interviewers probe:**
- If the downstream service returns an HTTP error (404, 401, 503) — you can decode that
  response and map it to a meaningful exception on your side.
- If the downstream service is **completely unreachable** (connection refused, DNS failure) —
  there is no HTTP response at all, so response-based error handling never fires. You need a
  separate catch for connection-level exceptions (e.g. Feign's `RetryableException`), typically
  converted into a `ServiceUnavailableException` → HTTP 503 to your own caller. This
  distinction — "response error vs connection error are handled in completely different code
  paths" — is a strong, specific answer if asked how you handle downstream failures.
- Production-grade version of this: wrap the Feign call in a **circuit breaker** (Resilience4j)
  with a fallback method, so repeated failures stop hammering a dead service and instead return
  a fast, predictable fallback response.

---

## 5. Exception Handling

**Why a centralized framework instead of scattered try/catch:** consistency (every error looks
the same shape to API consumers), and a single place to map exception → HTTP status, instead of
repeating that logic in every controller.

**Standard pattern:**
1. Define a small hierarchy of custom exceptions that mean something business-wise:
   `ResourceNotFoundException`, `ResourceAlreadyExistsException`, `UnauthorizedException`,
   `ServiceUnavailableException`, `ValidationException`, etc. Throw these from service layer
   code — never let raw `RuntimeException` leak out as the "meaning" of an error.
2. Define one consistent error response shape:
   ```json
   { "timestamp": "...", "status": 404, "error": "Not Found", "message": "...", "path": "/api/..." }
   ```
3. One `@RestControllerAdvice` class with `@ExceptionHandler` methods per exception type,
   mapping each to the right HTTP status. This is where `MethodArgumentNotValidException`
   (from `@Valid` failures) also gets normalized into the same shape instead of returning a raw
   stack trace or Spring's default validation error format.

**In a distributed system, exception handling has an extra layer:** you also need to translate
*downstream* failures (another service's error, or a network failure) into your own consistent
exception shape before they reach your own caller. That means a **Feign error decoder** — a
component that inspects HTTP status codes coming back from Feign calls and maps them to your
own custom exceptions (404→`ResourceNotFoundException`, 401→`UnauthorizedException`,
503→`ServiceUnavailableException`), registered via a `FeignConfig` bean so it applies to every
Feign client automatically.

**The critical nuance again (worth repeating because it's commonly missed):** an error decoder
only runs when there *is* an HTTP response. A connection-level failure (service is completely
down) never reaches the decoder — you need separate handling for that case (catch the
connection exception directly, or use a circuit breaker with a fallback).

---

## 6. Service Discovery

**The problem:** hardcoded addresses (`http://localhost:8081`) break the moment a service
moves — different host, different port, container restart, horizontal scaling to multiple
instances. In real deployments, instances come and go constantly.

**The fix — a registry (Eureka, Consul, or cloud-native equivalents like Kubernetes' built-in
DNS-based discovery):**
1. Every service **registers itself** on startup — sends its name, host, port.
2. Every service sends periodic **heartbeats** to prove it's still alive.
3. When Service A needs Service B, it asks the registry "where is service-B right now?"
   instead of using a hardcoded address.

**Eureka specifics you should know:**
- Server side: `@EnableEurekaServer`, own project, `register-with-eureka=false` and
  `fetch-registry=false` on the server itself (it's not also a client).
- Client side: just adding the `eureka-client` dependency + `spring.application.name` +
  `eureka.client.service-url.defaultZone` is enough — Spring Boot auto-registers, no extra
  annotation needed.
- Once registered, Feign clients drop the hardcoded `url` attribute and resolve by
  `spring.application.name` alone.

**Real bug worth knowing:** on some networks, Eureka registers instances under the machine's
hostname instead of its IP, and other services/gateways can't resolve that hostname. Fix:
`eureka.instance.prefer-ip-address=true` forces registration by IP. If a gateway or Feign call
suddenly starts throwing `UnknownHostException` after adding Eureka, this is the first thing to
check.

**Self-preservation mode** — Eureka's defensive behavior of *not* evicting instances when it
sees a broad heartbeat drop (protects against a network blip looking like mass outage). Normal
and expected in local dev with few instances; not something you need to "fix."

**Why this matters beyond Eureka:** the generic concept — "don't hardcode network locations,
resolve them dynamically at call time" — is the same idea behind Kubernetes Services, Consul,
and DNS-based service meshes. Eureka is just one implementation of client-side service
discovery.

---

## 7. API Gateway

**The problem after service discovery alone:** internal services can now find each other
dynamically, but *external clients* still need to know every individual service's address —
multiple public endpoints, repeated CORS config, no single place to apply cross-cutting
concerns (auth, rate limiting, logging).

**The fix:** one entry point. Clients only ever talk to the gateway; the gateway routes to the
right internal service based on the request path.
```
Client → API Gateway → Service A
                     → Service B
```

**Spring Cloud Gateway basics:**
- Built on WebFlux (reactive), not the older Zuul/MVC-style gateway — this is the modern
  default in the Spring Cloud ecosystem.
- Routes are defined by **predicates** (usually path patterns) and a **target URI**.
- The `lb://SERVICE-NAME` prefix (instead of a fixed host:port) tells the gateway "resolve this
  destination through the load balancer + service registry at request time" — this is the
  detail interviewers specifically probe for ("how does the gateway actually find the
  instance?"). Answer: gateway → asks Eureka (or whatever registry) for current instances of
  SERVICE-NAME → picks one (client-side load balancing) → forwards the request.

**What a gateway is the natural place to centralize:**
- Authentication/token validation before requests even reach services (though per-service
  validation as defense-in-depth is still good practice — "don't trust the gateway alone").
- CORS configuration (once, not per service).
- Rate limiting / request throttling.
- Centralized logging of all inbound traffic.
- Path-based or header-based routing, request/response transformation.

**One subtlety worth remembering:** a single client request can trigger *two separate*
discovery lookups — one by the gateway to find the target service, and a second one if that
service then calls another service internally via Feign. Both use the same registry
independently; they're not the same lookup.

---

## 8. Unit Testing (Service Layer, in isolation)

**The mental model:** a unit test for the service layer never touches the database, never
starts the Spring context, never makes a real HTTP/Feign call. Every collaborator the service
depends on is replaced with a mock.
```
Production: Service → Repository → Database
Unit test:  Service → Mock Repository (Mockito)
```
This is what makes unit tests run in milliseconds and stay reliable regardless of environment.

**Tooling:** JUnit 5 for test structure (`@Test`, `@BeforeEach`, assertions), Mockito for fakes
(`@Mock`, `@InjectMocks`, `when(...).thenReturn(...)`, `verify(...)`), JaCoCo for coverage
reporting (`mvn clean test` → HTML report of covered/uncovered lines and branches).

**What you actually test in a service class:**
- The happy path (create/get/update/delete succeeds).
- Duplicate/conflict cases (resource already exists).
- Not-found cases (repository mock returns empty `Optional`).
- Authorization/ownership logic (does the service correctly reject an action on a resource
  that belongs to a different user?).
- Downstream failure handling (mock a Feign client to throw, verify the service converts it to
  the right exception).

**Two mocking patterns that come up in almost every real service and are worth memorizing:**

1. **Mocking Spring Security when the service reads the current user from
   `SecurityContextHolder`:**
   ```
   Mock Authentication → Mock SecurityContext → SecurityContextHolder.setContext(...)
   ```
   You build a fake `Authentication` object with the userId you want, wrap it in a mocked
   `SecurityContext`, and set it as the thread's context before calling the service method —
   letting you test authenticated logic without ever starting Spring Security.

2. **Mocking Feign clients for services that call other services:**
   ```java
   when(authClient.getUserByPublicProfileId(anyString())).thenReturn(fakeUserDto);
   ```
   Lets you test the orchestration logic (what happens with the response, what happens if it
   throws) with zero real network calls.

**Why this matters for interviews:** "how do you unit test a service with a dependency on
authentication / another microservice" is a very common follow-up, and both patterns above are
concrete, correct answers — much stronger than a vague "I use Mockito."

---

## 9. Event-Driven Architecture (Kafka)

**When to reach for this vs a direct API call (the core design decision):** ask "does my
response depend on this side-effect succeeding?" If no — event-driven. If yes — synchronous
call (see topic 4). Classic example you gave: **notification after a successful payment.**

**How you'd design "notify user after payment succeeds," generically:**
```
Payment Service: charge succeeds → persist payment record → publish PaymentSucceededEvent
                 → return success response to client IMMEDIATELY (doesn't wait on notification)

Notification Service: subscribed to the topic → consumes PaymentSucceededEvent
                 → sends email/SMS/push → done, independently, at its own pace
```
Why this is the right shape: the client shouldn't see a slow/failed API response just because
an email provider is having a bad day. Payment succeeding and notifying the user are two
separate business concerns with two separate failure domains — decouple them.

**Same pattern generalizes to:** order-placed → inventory service decrements stock;
user-registered → welcome-email service sends email; emergency-profile-accessed → audit service
logs it (your actual MedInfo example). The shape is always: **producer publishes a fact that
happened; any number of consumers react to it independently, with zero code change required on
the producer side to add a new consumer.**

**Core Kafka vocabulary, precisely (interviewers check for precision here):**
- **Topic** — named stream of events (e.g. `payment-succeeded-events`).
- **Partition** — a topic is split into partitions for parallelism; order is only guaranteed
  *within* a partition, not across the whole topic.
- **Producer** — publishes; **Consumer** — reads.
- **Consumer group** — consumers sharing work; Kafka guarantees each partition is read by only
  one consumer within a group at a time — this is what gives you automatic load balancing when
  you scale consumer instances.
- **Offset** — the consumer group's bookmark in a partition; Kafka remembers where each group
  left off, so a consumer that comes back online resumes rather than reprocessing everything (as
  long as it hadn't committed past those messages).
- **Message key** — choosing a key (e.g. `userId`) sends all events for that key to the same
  partition, guaranteeing per-user (or per-entity) ordering.

**Producer side, what actually matters in code terms:**
```java
kafkaTemplate.send(topicName, event.getUserId().toString(), event);
```
Topic, key, payload — that's the whole call. Serialization to JSON is handled by configuring
a `JsonSerializer` for the value.

**Consumer side:**
```java
@KafkaListener(topics = "payment-succeeded-events", groupId = "notification-service-group")
public void handle(PaymentSucceededEvent event) { ... }
```

**The event-class-ownership gotcha (a genuinely important, often-missed detail):** the producer
and consumer should each define their **own copy** of the event class in their own package —
not share a JAR/library containing the event class. Why: sharing a class binds the two services
together at compile time, which defeats the point of decoupling them. What's actually shared is
the **JSON contract** (the field names/types), not a Java class. Practically this means
configuring `trusted.packages` on the consumer side to point at its own local event package, not
the producer's.

**What you put in the event payload — a subtle but important choice:** include the moment
something happened as data in the event itself (e.g. `accessedAt` / `occurredAt` timestamp),
rather than relying on the consumer's persistence-time timestamp. If the consumer processes the
event five minutes late (backlog, retry, etc.), the record should still reflect when the event
*actually* happened, not when it was *processed*.

---

## 10. Kafka Reliability (Retry, DLT, Idempotency)

Three separate problems, three separate fixes — don't blend these into one vague "error
handling" answer; interviewers specifically like probing whether you know they're distinct.

**Problem 1 — transient consumer failures** (DB momentarily down, brief network blip).
**Fix: Retry.** Configure a `DefaultErrorHandler` with a backoff policy (e.g. `FixedBackOff`:
retry N times with a fixed delay between attempts), registered on the
`ConcurrentKafkaListenerContainerFactory`. If the exception is transient, a later retry
succeeds and processing continues normally.

**Problem 2 — a "poison message"** that will *never* succeed no matter how many times you
retry (malformed payload, a permanent bug for that specific message). Left unhandled, this
blocks the partition — every subsequent message behind it also blocks, since order is retried
in place. **Fix: Dead Letter Topic (DLT).** After retries are exhausted, a
`DeadLetterPublishingRecoverer` republishes the failed message to a separate topic (conventionally `<topic>.DLT`) instead of discarding it or blocking the partition — preserving it for investigation/replay while
the rest of the partition keeps moving. Good practice: preserve the original partition number
when writing to the DLT, so partition-to-partition correlation is easy to trace later.
Ownership note: DLT/retry config generally belongs on the **consumer** side, since the consumer
owns "what happens when I fail to process this."

**Problem 3 — duplicate delivery.** Kafka guarantees **at-least-once** delivery by default —
meaning a message *can* be redelivered (e.g., consumer crashes after saving to DB but before
committing its offset — on restart, Kafka redelivers the same message). Without protection,
this creates duplicate rows/duplicate side-effects (imagine sending the same notification
email twice). **Fix: Idempotent consumer.** Give every event a unique `eventId` (UUID,
generated by the producer). On the consumer side, check "have I already processed this
eventId?" before doing the write — typically a unique DB constraint on `eventId` plus an
`existsByEventId()` check before insert. If it already exists, skip; if not, process and save.

**Full reliability flow, stitched together:**
```
Consume event → existsByEventId()?
   yes → ignore (duplicate, already processed)
   no  → process → save
           on exception → retry (fixed backoff, N attempts)
              still failing after retries → publish to DLT (don't block partition, don't drop)
```

**Why this matters — the generic lesson:** "consuming Kafka messages" and "reliably consuming
Kafka messages in production" are different skill levels. At-least-once delivery + partition
ordering + retryable vs permanent failures together mean you always need some combination of
retry, DLT, and idempotency for any consumer that has real side effects (writing to a DB,
sending an email, charging a card).

---

## 11. Redis

*(Not yet implemented in your project — generic knowledge to have ready.)*

**What it actually is:** an in-memory key-value store, used any time you need something faster
than a database round-trip and don't need full relational query power.

**The three uses that come up constantly in interviews:**

1. **Caching** — store the result of an expensive/frequent read (e.g., a product catalog
   lookup, a user profile lookup) so repeat requests skip the database.
   - Pattern: cache-aside (most common) — on read, check Redis first; on miss, read from DB and
     populate Redis; on write/update, invalidate (or update) the cache entry.
   - Set a TTL (time-to-live) on cached entries so stale data doesn't live forever — critical
     decision: how long can this data be "wrong" before it matters?
   - Spring integration: `@Cacheable`, `@CacheEvict`, `@CachePut` annotations with
     `spring-boot-starter-cache` + a Redis `CacheManager`, or manual `RedisTemplate` calls for
     finer control.

2. **Session storage** — in a horizontally-scaled service that still needs server-side session
   state (not everything is pure JWT), Redis lets any instance read/write the same session data
   instead of each instance holding its own in-memory session (which breaks the moment a
   request lands on a different instance).

3. **Rate limiting** — Redis's atomic increment (`INCR`) + expiry is a natural fit for "allow N
   requests per user per minute": increment a counter keyed by user+time-window, set an
   expiry equal to the window, reject once the count passes the threshold.

**Other realistic uses:** distributed locks (e.g., prevent two instances from processing the
same job simultaneously — `SETNX` semantics), leaderboards/counters (atomic increment
operations), pub/sub for lightweight real-time messaging (different from Kafka — Redis pub/sub
has no persistence/replay; it's fire-and-forget for connected subscribers only).

**Cache invalidation, the classic hard problem:** the two-line summary you should be able to
give — write-through (update cache and DB together) vs write-behind (update cache, flush to DB
async) vs cache-aside with explicit eviction on write (simplest, most common in real Spring
apps). Always pair a cache with a TTL as a safety net even if you invalidate explicitly, in
case an eviction path gets missed somewhere.

---

## 12. Docker

*(Not yet implemented — generic knowledge to have ready.)*

**What problem it solves:** "works on my machine" — packaging an application with its runtime,
dependencies, and config into one portable image that runs identically anywhere Docker runs.

**Core building blocks:**
- **Dockerfile** — recipe for building an image: base image (e.g. a JDK image), copy the built
  artifact (JAR), expose a port, define the startup command.
  ```dockerfile
  FROM eclipse-temurin:21-jre
  COPY target/app.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- **Image vs container** — an image is the built, immutable package; a container is a running
  instance of that image. You can run many containers from one image.
- **Multi-stage builds** — a common real-world pattern for Spring Boot: one stage with the full
  Maven/JDK toolchain builds the JAR, a second, much smaller stage (JRE-only base image) just
  copies the built JAR in. This keeps the final image small since the build tools aren't shipped
  in production.

**Why this matters for a microservices project specifically:** every service (auth, gateway,
each domain service, even Eureka) becomes its own image, runnable independently, which
directly enables Docker Compose (local multi-service orchestration) and later real deployment
(Kubernetes/ECS/etc.) — Docker is the common packaging format underneath all of it.

**Practical things worth knowing, not just theory:**
- `.dockerignore` to keep build context small (don't ship `.git`, `target/`, IDE files into the
  build).
- Environment variables (`ENV`, or passed at `docker run -e`) for config that changes per
  environment (DB URLs, secrets) — never bake secrets into the image itself.
- Networking: containers on the same Docker network can reach each other by container/service
  name, not `localhost` — a common source of confusion when moving from "everything on my
  laptop's localhost" to containers.

---

## 13. Docker Compose

*(Not yet implemented — generic knowledge to have ready.)*

**What it's for:** running a multi-container application (all your services + their databases +
Kafka + Redis + Eureka, etc.) together with one command, instead of manually starting each
container and wiring up networking by hand.

**Core idea — a `docker-compose.yml` describing the whole system:**
```yaml
services:
  eureka-server:
    build: ./eureka-server
    ports: ["8761:8761"]

  auth-service:
    build: ./auth-service
    ports: ["8081:8081"]
    environment:
      - EUREKA_URI=http://eureka-server:8761/eureka
    depends_on: [eureka-server, auth-db]

  auth-db:
    image: postgres:16
    environment:
      - POSTGRES_DB=auth_db
```

**Concepts that actually get asked about:**
- **Service name = hostname** — inside the Compose network, `auth-service` reaches
  `eureka-server` by the service name `eureka-server`, not `localhost`. This is the same
  networking model as topic 12, just automated by Compose.
- **`depends_on`** controls startup *order*, not readiness — a database container starting
  doesn't mean it's ready to accept connections yet. Real fix: retry/backoff on the app side
  (Spring Boot's connection retry, or a healthcheck + `condition: service_healthy` in Compose)
  rather than assuming `depends_on` alone guarantees the dependency is usable.
- **Named volumes** for anything that needs to survive a container restart (Postgres data
  directory, for example) — without a volume, data disappears when the container is removed.
- **One command spins up the whole system:** `docker compose up` — this is what makes local
  development of a multi-service architecture actually manageable; without it you'd be manually
  starting 6+ services and their infra in the right order every time.

---

## 14. CI/CD

*(Not yet implemented — generic knowledge to have ready.)*

**The distinction to state precisely:**
- **CI (Continuous Integration)** — every code push automatically builds the project and runs
  tests, catching breakages immediately instead of at manual QA time.
- **CD (Continuous Delivery/Deployment)** — a passing build is automatically packaged (e.g. a
  Docker image) and, depending on how far you automate, either staged for manual release
  approval (delivery) or pushed straight to production (deployment).

**A realistic pipeline for a Spring Boot microservice, stage by stage:**
```
Push to branch → checkout → build (mvn/gradle) → run unit tests (+ JaCoCo coverage gate)
   → build Docker image → push image to a registry (Docker Hub / ECR / GHCR)
   → deploy (to staging automatically, to production on merge-to-main or manual approval)
```

**GitHub Actions specifics worth knowing (most common tool to be asked about):**
- A workflow is a YAML file under `.github/workflows/`, triggered on events (`push`,
  `pull_request`).
- Jobs run in isolated runners; steps within a job share a filesystem.
- Secrets (DB credentials, registry tokens) go into GitHub Secrets, injected as environment
  variables at runtime — never hardcoded in the workflow file.
- A typical job: checkout → set up JDK → cache Maven/Gradle dependencies (huge speed win on
  repeat runs) → `mvn test` → build/push Docker image.

**Why tests-in-CI matters beyond "best practice":** this is the direct payoff of topic 8 (unit
testing) — if your service layer wasn't unit-testable in isolation (no Spring context needed),
CI runs would be slow and flaky. Fast, isolated unit tests are what make a CI pipeline actually
useful as a gate rather than a bottleneck.

**In a microservices monorepo specifically:** a common refinement is triggering builds only for
the services actually changed (path filters on the workflow trigger), so one small change to
`audit-service` doesn't rebuild and redeploy every other service.

---

## 15. Logging & Monitoring

*(Not yet implemented — generic knowledge to have ready.)*

**Why this becomes non-optional in microservices specifically:** in a monolith, `grep`-ing one
log file was almost enough. In microservices, a single client request can touch 4–5 services —
without correlation, you cannot reconstruct what happened across service boundaries when
something breaks.

**Structured logging basics:**
- Log in a structured format (JSON) rather than free-text, so logs are machine-searchable by
  field (service name, level, userId, etc.), not just grep-able strings.
- Use proper log levels deliberately: `ERROR` (something broke, needs attention), `WARN`
  (unexpected but handled), `INFO` (significant business events — "order placed", "payment
  processed"), `DEBUG` (verbose, dev-time only, off in production by default).

**Correlation IDs — the single most important microservices-logging concept:**
- Generate a unique ID (or propagate one from an incoming header) at the edge (API Gateway or
  the first service that receives the request).
- Pass that ID downstream on every subsequent call (Feign header, Kafka event field).
- Every service logs that same ID alongside its own logs.
- Result: you can search "all logs for correlation ID X" across every service and reconstruct
  the full request path — this is what actually lets you debug a distributed failure instead of
  guessing.

**Centralized log aggregation:** ship logs from every service/container to one place instead of
SSH-ing into individual machines. Common stacks: **ELK/EFK** (Elasticsearch + Logstash/Fluentd +
Kibana) or lighter alternatives (Loki + Grafana). The pattern is always: collect → ship → index
→ search/visualize in one UI.

**Monitoring vs logging — different tools for a different question.** Logging answers "what
happened in this specific request." Monitoring/metrics answers "how is the system behaving in
aggregate over time" — request rate, error rate, latency percentiles (p50/p95/p99), resource
usage. Spring Boot Actuator exposes health/metrics endpoints out of the box; **Prometheus**
scrapes those metrics on an interval; **Grafana** visualizes them as dashboards. This combo
(Actuator + Prometheus + Grafana) is close to a de facto standard in the Spring ecosystem.

**Health checks** (`/actuator/health`) matter beyond dashboards — orchestrators (Kubernetes,
Docker Compose healthchecks) use them to decide whether to route traffic to an instance or
restart it.

**Alerting** — the last mile: metrics/logs are only useful operationally if breaching a
threshold (error rate spike, latency spike, service down) actually notifies someone, rather than
sitting in a dashboard nobody's watching.

---

## 16. Cloud Deployment

*(Not yet implemented — generic knowledge to have ready.)*

**The mental model to hold:** everything in topics 1–15 (services, Docker images, Compose
topology, CI/CD pipeline) is what gets deployed — cloud deployment is "where does this actually
run, and who/what keeps it running."

**Deployment target options, roughly in order of how much infrastructure you manage yourself:**
- **PaaS (Railway, Render, Heroku-style)** — push code/image, platform handles servers,
  scaling knobs are simple. Fastest to get a personal/learning project live; least control.
- **Container orchestration (Kubernetes, or managed variants — EKS/GKE/AKS)** — you define
  desired state (how many replicas, resource limits, health checks) and the orchestrator
  maintains it: restarts crashed containers, load-balances across replicas, supports rolling
  deployments.
- **Managed container services (AWS ECS/Fargate, Google Cloud Run)** — a middle ground: still
  container-based, less operational overhead than raw Kubernetes.
- **Raw VMs (EC2, etc.)** — full control, full responsibility; you manage everything Docker/K8s
  would otherwise handle for you.

**Concepts worth being fluent in regardless of which target you use:**
- **Environment-based config** — the same image/artifact should run in dev/staging/prod purely
  by changing environment variables/config, never by rebuilding with different hardcoded values.
  This is what makes the same Docker image promotable across environments.
- **Secrets management** — DB passwords, JWT secrets, API keys never live in source control or
  baked into images. Cloud-native secret stores (AWS Secrets Manager, GCP Secret Manager,
  Kubernetes Secrets) or environment injection at deploy time.
- **Horizontal scaling** — running multiple instances of a stateless service behind a load
  balancer. This is exactly why earlier decisions (stateless JWT auth, no server-side session
  affinity) matter — a service that assumes "the next request from this user hits the same
  instance" breaks the moment you scale horizontally.
- **Rolling deployments / zero-downtime deploys** — new instances start and pass health checks
  before old instances are terminated, so a deploy doesn't cause a visible outage.
- **Managed databases** (RDS, Cloud SQL, or your existing Neon setup) instead of self-hosting
  Postgres on a VM — offloads backups, patching, failover to the provider.
- **For microservices specifically:** each service typically gets its own deployment unit
  (separate scaling, separate rollout, separate rollback) — the deployment topology should
  mirror the service boundaries you designed in topic 2, not undo them by deploying everything
  as one bundle again.

---

## Quick-reference: the "why this, not that" table

Use this as a fast oral-answer cheat sheet — these are the comparisons interviewers actually ask.

| Question | Short answer |
|---|---|
| Session vs JWT? | Session = server remembers, doesn't scale without shared store. JWT = client carries proof, any instance can validate it. |
| Sync (Feign) vs async (Kafka) between services? | Sync when your response needs their answer to proceed. Async when it's a side-effect the caller shouldn't wait on. |
| Feign error decoder vs connection failure handling? | Decoder only fires on an actual HTTP response; total outage (no response) needs separate connection-exception handling. |
| Why database-per-service? | Prevents hidden coupling through shared tables; forces all cross-service access through versioned APIs. |
| Retry vs DLT vs idempotency? | Retry = transient failure. DLT = permanent failure, don't block the partition. Idempotency = protect against Kafka's at-least-once redelivery creating duplicates. |
| Eureka vs API Gateway — different jobs? | Eureka = services finding *each other*. Gateway = external clients finding the *system*, plus a place for cross-cutting concerns. |
| Unit test vs integration test? | Unit = service logic only, everything mocked, no Spring context. Integration = real Spring context, often real (or test-container) DB, verifies wiring. |
| Docker vs Docker Compose? | Docker packages one service. Compose orchestrates several services + their infra together locally with one command. |