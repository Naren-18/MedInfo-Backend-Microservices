# Day 8 — Logging Architecture & Implementation

## 🏗 MedInfo Microservices — Day 8

Day 8 covers designing a layered logging strategy and implementing it across
all five services — request logging, business-event logging, Kafka
producer/consumer logging, Redis cache logging, and exception logging — along
with two real bugs hit and fixed while wiring it all in.

---

## 📌 Chapter 33 — The 5-Layer Logging Model

Logging was designed around five layers, each with a distinct responsibility:

```
Request
    │
    ▼
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
Infrastructure
```

| Layer | Responsibility | Volume |
|---|---|---|
| **1. Request** | Every incoming request/response — method, URL, IP, User-Agent, status, time taken | Highest priority |
| **2. Controller** | Almost nothing — controllers aren't where business happens | Minimal, avoid duplication |
| **3. Service** | Every important business action — the bulk of all logging | ⭐ Most logs belong here |
| **4. Repository** | Almost never — Hibernate already logs SQL | Rare |
| **5. Infrastructure** | Startup/connection events — Redis connected, Kafka consumer started, Eureka registered | Sparse, high-signal |

### Log Levels Used

| Level | Meaning | Examples |
|---|---|---|
| `INFO` | Normal business flow | User Created, Cache HIT, Kafka Event Published |
| `WARN` | Unexpected but the application continues correctly | Cache MISS, Duplicate Event Ignored, Retry Attempt |
| `ERROR` | Something actually failed | DB connection failed, Feign timeout, Event moved to DLT |
| `DEBUG` | Developer-only detail (JWT claims, SQL params, Feign payloads) | Not implemented this session — left for later, normally disabled in prod |

### What Was Deliberately *Not* Logged

No "Method Started" / "Method Ended" / "Controller Entered" noise anywhere.
Every log line states a **business fact** ("User registered successfully"),
never a **code-execution fact** ("entered method X").

### Structured, Parameterized Logging

Every log call uses SLF4J's `{}` placeholders, never string concatenation:

```java
// Not this:
log.info("User Created " + id);

// This:
log.info("User created successfully. UserId={}", userId);
```
Cheaper (no string-building unless the log level is actually enabled),
consistent, and greppable in any log aggregation tool.

---

## 📌 Chapter 34 — Layer 1: Request Logging

### Auth Service & Medical Service — `RequestLoggingFilter`

Both services got their own copy of the same filter (consistent with the
"each service owns its own copy" pattern already established for Kafka event
classes):

`auth-service/src/main/java/com/medinfo/auth/Filter/RequestLoggingFilter.java`
(medical-service has an identical one in its own `Filter` package):

```java
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        log.info("Incoming Request. Method={}, URL={}, IP={}, UserAgent={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));

        filterChain.doFilter(request, response);

        long timeTaken = System.currentTimeMillis() - startTime;
        log.info("Completed Request. Status={}, TimeTakenMs={}",
                response.getStatus(),
                timeTaken);
    }
}
```

**Why `OncePerRequestFilter`:** it guarantees `doFilterInternal` runs exactly
once per request even if the servlet container internally forwards/includes
the request elsewhere — without it, internal dispatches can cause duplicate
log lines.

**Why the timing works this way:** `filterChain.doFilter(request, response)`
is a blocking, synchronous call — it walks the rest of the filter chain,
hits the controller, and only returns once the full response has been
written. So the "Completed Request" log runs *after* everything downstream
finishes, and `response.getStatus()` already holds the real final status
code by then.

The "Incoming Request" log is placed **before** the chain runs — so even if
a request throws an unhandled exception downstream, there's still proof the
request was received, which matters when tracing a request that never got a
matching "Completed" line.

### Wiring It In — `SecurityCofig`

```java
private final JWTAuthenticationFilter jwtAuthenticationFilter;
private final RequestLoggingFilter requestLoggingFilter;

// ...
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
http.addFilterBefore(requestLoggingFilter, JWTAuthenticationFilter.class);
```

### ⚠️ Real Issue Hit — Filter Registration Order

The **order of these two calls matters**, and the first attempt had them
backwards:

```java
// WRONG — fails at context startup
http.addFilterBefore(requestLoggingFilter, JWTAuthenticationFilter.class);
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

**Root cause:** Spring Security tracks filter order internally via a
`FilterOrderRegistration`. Calling `addFilterBefore(newFilter, anchorClass)`
requires Spring Security to already know where `anchorClass` sits in the
chain. It only knows this in two cases:

1. `anchorClass` is one of Spring Security's own standard filters (like
   `UsernamePasswordAuthenticationFilter`) — these have a hardcoded default
   position baked into Spring Security's `FilterComparator`.
2. `anchorClass` was **already positioned** by an earlier `addFilterBefore`/
   `After`/`At` call in the same chain-building method.

`JWTAuthenticationFilter` is a custom filter — it has no built-in position.
The first line above asked Spring Security to place `requestLoggingFilter`
before a filter class whose position hadn't been registered yet, producing
this at context startup:

```
Failed to instantiate [SecurityFilterChain]: Factory method 'securityFilterChain' threw exception with message:
The Filter class com.medinfo.auth.Security.JWTAuthenticationFilter does not have a registered order
```

**Fix:** swap the two lines. Registering `jwtAuthenticationFilter`'s
position first (relative to the standard `UsernamePasswordAuthenticationFilter`)
implicitly establishes `JWTAuthenticationFilter.class`'s position, so
`requestLoggingFilter` can then validly be placed before it. Final runtime
order: `RequestLoggingFilter → JWTAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...`
— every request gets logged first, even ones hitting `permitAll()` endpoints
that never reach JWT validation.

This bug didn't surface in the Mockito-based unit tests (they never build a
real `SecurityFilterChain` bean) — only the `@SpringBootTest` integration
tests (`AuthServiceApplicationTests`, `MedicalServiceApplicationTests`),
which construct the real chain, caught it. Same bug, same fix, applied
independently in both `auth-service` and `medical-service` — each had
written its `SecurityCofig` the same way.

### Gateway Service — `LoggingGlobalFilter`

The Gateway is reactive (Spring Cloud Gateway on WebFlux), so it can't use a
servlet `Filter` — it needs a `GlobalFilter`:

`gateway-service/src/main/java/com/medinfo/gateway_service/Filter/LoggingGlobalFilter.java`:

```java
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unmatched";

        log.info("Forwarding Request. Route={}, Path={}", routeId, path);

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
```

Two things are genuinely different here from the servlet-based filters:

**No Lombok on the classpath.** `gateway-service`'s `pom.xml` never pulled
in Lombok — it's a thin routing layer, not a domain service. The first
attempt used `@Slf4j` and failed to compile:
```
package lombok.extern.slf4j does not exist
```
Fixed by using a plain SLF4J `Logger` via `LoggerFactory.getLogger(...)`
instead of adding a new dependency for one filter class.

**Reactive execution model.** `chain.filter(exchange)` returns a `Mono<Void>`
*immediately* — a pipeline description, not a completed result. Unlike the
synchronous `OncePerRequestFilter`, code written after `chain.filter(exchange)`
in the method body runs immediately, **before** the actual proxying happens
— it does not run "after the request completes" the way it would in a
blocking servlet filter. That's why this filter only logs *before*
forwarding, matching the single example in the logging spec ("Forwarding
Request") rather than a before/after pair. A true after-the-fact log would
need `.then(Mono.fromRunnable(() -> log.info(...)))` chained onto the
returned `Mono` — deliberately left out to match the actual scope needed.

**`getOrder() = Ordered.LOWEST_PRECEDENCE`** — global filters run in
ascending order of `getOrder()`, and `LOWEST_PRECEDENCE` is the largest
possible int, so this filter runs *last* among global filters. This is
intentional: `exchange.getAttribute(GATEWAY_ROUTE_ATTR)` is only populated
once Spring Cloud Gateway's internal route-matching has already run. Using
`HIGHEST_PRECEDENCE` risked this filter executing before routing was
resolved, leaving `route` `null` on every request — hence also the
defensive `route != null ? route.getId() : "unmatched"` fallback.

---

## 📌 Chapter 35 — Layer 2: Controllers (Deliberately Minimal)

The logging spec's own example for this layer was:
```java
log.info("Request received for emergency profile {}", publicProfileId);
```
placed in `EmergencyController`. This was **not added**, deliberately: by
the time a request reaches `EmergencyController`, `RequestLoggingFilter`
has already logged the method/URL (Layer 1), and `EmergencyService` (see
Chapter 36) now logs `Emergency Profile Requested. PublicProfileId={}` as
the very first line of `getEmergencyProfile()`. Adding a third, near-identical
log line at the controller would just be noise — it contradicts the stated
goal of "clean, production-style logging," so Layer 2 was left empty across
every controller in the project.

---

## 📌 Chapter 36 — Layer 3: Service Logging (The Bulk of the Work)

### Auth Service — `AuthService.java`

```java
public String register(RegisterRequestDTO registerRequestDTO){
    log.info("Registration requested. Email={}", registerRequestDTO.getEmail());
    if(userRepository.existsByEmail(registerRequestDTO.getEmail())){
        throw new ResourceAlreadyExistsException("User", "email", registerRequestDTO.getEmail());
    }
    User user = User.builder()...build();
    userRepository.save(user);
    log.info("User registered successfully. UserId={}", user.getId());
    return "User Registration Completed";
}

public String login(LoginRequestDTO loginRequestDTO){
    log.info("Login requested. Email={}", loginRequestDTO.getEmail());
    User user = userRepository.findByEmail(loginRequestDTO.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid Credentials"));
    boolean result = passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword());
    if(!result){
        log.warn("Invalid login attempt. Email={}", loginRequestDTO.getEmail());
        throw new UnauthorizedException("Password Invalid");
    }
    String token = jwtService.generateToken(user);
    log.info("JWT generated. UserId={}", user.getId());
    return token;
}
```

- Logged **before** the duplicate-email check, so intent is always visible
  even when the operation later fails.
- The success log runs **after** `save()`, so `user.getId()` reflects the
  actual persisted, DB-generated ID rather than a pre-save transient value.
- A failed password check is logged at `WARN`, not `INFO` — it's
  "unexpected but the application continues correctly," exactly matching
  the level's definition; a wrong password is not a system failure.

### Medical Service — `MedicalProfileService.java` / `EmergencyContactsService.java`

Straightforward CRUD lifecycle logs on every create/update/delete:

```java
public MedicalProfile createProfile(CreateMedicalProfileDTO dto){
    Long userId = getCurrentUserId();
    log.info("Creating Medical Profile. UserId={}", userId);
    // ...
    MedicalProfile saved = medicalProfileRepository.save(medicalProfile);
    log.info("Medical Profile Created Successfully. UserId={}", userId);
    return saved;
}
```

`EmergencyContactsService` additionally logs `WARN` on ownership violations
— a user attempting to modify a contact that isn't theirs:

```java
if (!emergencyContacts.getUserId().equals(userId)) {
    log.warn("Unauthorized attempt to update contact. ContactId={}, UserId={}", id, userId);
    throw new UnauthorizedException("Unauthorized");
}
```

### `EmergencyService.java` — the Highest-Traffic Path

```java
public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId, HttpServletRequest request){
    log.info("Emergency Profile Requested. PublicProfileId={}", publicProfileId);

    EmergencyProfileResponseDTO cacheresponseDTO = cacheService.getEmergencyProfile(publicProfileId);
    if (cacheresponseDTO != null) {
        log.info("Cache HIT. PublicProfileId={}", publicProfileId);
        return cacheresponseDTO;
    }
    log.warn("Cache MISS. Loading from Database. PublicProfileId={}", publicProfileId);
    // ...
```

Cache MISS is logged at `WARN`, not `INFO` — a miss means the request is
about to do real DB + Feign work instead of a fast in-memory read, which is
"more expensive than the fast path" even though it's expected behavior on a
cold cache. Cache HIT stays `INFO` since it's the common, cheap case.

The Auth Feign-call failure path was upgraded from silent-to-503 into
logged-and-503:

```java
try {
    user = authClient.getUserById(userId);
} catch (RetryableException ex) {
    log.error("Feign call to Auth Service failed. UserId={}", userId, ex);
    throw new ServiceUnavailableException("Auth Service is not available");
}
```

`RetryableException` signals there was *no HTTP response at all* (connection
refused, DNS failure) — this matches the spec's `ERROR` example "Feign
Client Timeout." It's genuinely unexpected infrastructure failure, distinct
from a normal, reachable 404/401.

### `EmergencyProfileCacheService.java`

```java
public void cacheEmergencyProfile(String publicProfileId, EmergencyProfileResponseDTO response){
    String cacheKey = getCacheKey(publicProfileId);
    redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(10));
    log.info("Emergency Profile Cached. PublicProfileId={}", publicProfileId);
}

public void evictEmergencyProfile(String publicProfileId){
    redisTemplate.delete(getCacheKey(publicProfileId));
    log.info("Cache Evicted. PublicProfileId={}", publicProfileId);
}
```
Reworded from the original ad-hoc messages (`"Cached emergency profile {}"`)
to match the spec's exact vocabulary (`Emergency Profile Cached`,
`Cache Evicted`), and moved after the Redis operation completes rather than
before — the log now confirms the action actually happened, not just that
it was attempted.

### `AuditEventProducer.java`

```java
// before
System.out.println("Publishing Event: " + event);

// after
log.info("Publishing Audit Event. EventId={}, UserId={}", event.getEventId(), event.getUserId());
```
`System.out.println` bypasses the logging framework entirely — no log
level, no logger name/class context, unfilterable, not routed by any log
config, inconsistent timestamps versus everything else. Replaced with a
parameterized SLF4J call carrying exactly the two fields the spec asked for.

---

## 📌 Chapter 37 — Audit Service: Kafka Producer/Consumer Logging

### `AuditService.java`

```java
public AuditLog createAuditLog(AuditLogEvent auditLogEvent){
    if (auditRepository.existsByEventId(auditLogEvent.getEventId())) {
        log.warn("Duplicate Audit Event Ignored. EventId={}", auditLogEvent.getEventId());
        return null;
    }
    AuditLog auditLog = AuditLog.builder()...build();
    AuditLog saved = auditRepository.save(auditLog);
    log.info("Audit Log Saved. EventId={}", saved.getEventId());
    return saved;
}
```
`existsByEventId` is the idempotency guard from the earlier Kafka-reliability
work — Kafka is at-least-once delivery, so the same event can legitimately
arrive twice (e.g. a consumer crash between processing and offset-commit). A
duplicate isn't an error and the app handles it correctly and keeps going —
exactly the definition of `WARN`, not `ERROR`.

### `AuditEventConsumer.java`

```java
@EventListener(ApplicationReadyEvent.class)
public void onStartup() {
    log.info("Audit Consumer Started. Topic={}", KafkaTopics.AUDIT_EVENTS);
}

@KafkaListener(topics = KafkaTopics.AUDIT_EVENTS, groupId = "audit-group")
public void consume(AuditLogEvent event){
    log.info("Received Audit Event. EventId={}, UserId={}", event.getEventId(), event.getUserId());
    auditService.createAuditLog(event);
}
```

For the "Consumer Started" log, `ApplicationReadyEvent` was used instead of
hooking into Kafka's own listener-container lifecycle callbacks (e.g.
implementing `ConsumerSeekAware`) — it fires exactly once, after the entire
Spring context has finished starting and is ready to serve traffic. Simple,
standard idiom for a one-time startup log.

The "Received" log was also moved to run **before** `auditService.createAuditLog(event)`
(it previously ran after) — so the event's arrival is visible in the logs
even if `createAuditLog` throws and the message enters the retry/DLT path
below.

### `KafkaConsumerConfig.java` — Retry & Dead Letter Topic Logging

This is the most involved change of the day, so it's worth walking through
the mechanics in full.

**Before:**
```java
@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(){
    return new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(KafkaTopics.AUDIT_EVENTS_DLT, record.partition())
    );
}

@Bean
public DefaultErrorHandler errorHandler() {
    FixedBackOff fixedBackOff = new FixedBackOff(1000L, 3L);
    return new DefaultErrorHandler(deadLetterPublishingRecoverer(), fixedBackOff);
}
```

**What each piece does:**
- `DefaultErrorHandler` is registered on the listener container
  (`factory.setCommonErrorHandler(errorHandler())`). Whenever
  `@KafkaListener consume(...)` throws, this object decides what happens next.
- `FixedBackOff(1000L, 3L)` — wait 1000ms between attempts, retry up to 3
  more times after the first failure (4 total attempts). Unchanged from
  earlier Kafka-reliability work.
- `DeadLetterPublishingRecoverer` implements `ConsumerRecordRecoverer`
  (`void accept(ConsumerRecord<?,?> record, Exception exception)`).
  `DefaultErrorHandler` calls this **exactly once**, only after all retries
  are exhausted. Its job is to republish the failed record onto
  `emergency-access-events.DLT`, on the *same partition number* as the
  source record — so partition-level ordering stays consistent between the
  main topic and its DLT.

**After — a logging decorator wrapped around the recoverer:**
```java
DeadLetterPublishingRecoverer recoverer = deadLetterPublishingRecoverer();

ConsumerRecordRecoverer loggingRecoverer = (record, ex) -> {
    Object value = record.value();
    String eventId = (value instanceof AuditLogEvent auditLogEvent)
            ? String.valueOf(auditLogEvent.getEventId())
            : "unknown";
    log.error("Audit Event moved to DLT. EventId={}, Reason={}", eventId, ex.getMessage());
    recoverer.accept(record, ex);   // delegate to the real DLT-publishing logic
};

DefaultErrorHandler errorHandler = new DefaultErrorHandler(loggingRecoverer, fixedBackOff);
errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
        log.warn("Retry Attempt {}. Partition={}, Offset={}, Reason={}",
                deliveryAttempt, record.partition(), record.offset(), ex.getMessage())
);
```

This is the **decorator pattern**: `loggingRecoverer` is a new lambda
implementing the same `ConsumerRecordRecoverer` interface. It logs an
`ERROR` line, then delegates to the original `recoverer.accept(record, ex)`
to actually perform the DLT publish. `DefaultErrorHandler` has no idea it's
talking to a wrapper — it just sees "something implementing
`ConsumerRecordRecoverer`."

**Why `instanceof AuditLogEvent auditLogEvent` instead of a plain cast:**
at this point `record.value()` is typed as `Object` (the interface is
generic-erased to `ConsumerRecord<?,?>`). Normally it really is an
`AuditLogEvent`, because `JsonDeserializer` already turned the bytes into
that object before the listener method ran and failed. But if
deserialization *itself* is what failed (e.g. malformed JSON from a
producer bug), `record.value()` might not be a valid `AuditLogEvent` at
all. Java's pattern-matching `instanceof` (rather than a hard cast) means a
bad record can't throw a `ClassCastException` **inside the error handler
itself** — it just logs `"unknown"` as the event ID instead of crashing
recovery.

**The retry-attempt logging:** `DefaultErrorHandler.setRetryListeners(...)`
registers a `RetryListener` — a separate functional interface
(`failedDelivery(ConsumerRecord<?,?> record, Exception ex, int deliveryAttempt)`)
that fires **after every single failed attempt**, not just the final one.
So a message that fails all 4 attempts produces:

```
WARN  Retry Attempt 1. Partition=0, Offset=42, Reason=...
WARN  Retry Attempt 2. Partition=0, Offset=42, Reason=...
WARN  Retry Attempt 3. Partition=0, Offset=42, Reason=...
WARN  Retry Attempt 4. Partition=0, Offset=42, Reason=...
ERROR Audit Event moved to DLT. EventId=..., Reason=...
```

Four `WARN`s (one per exhausted attempt) followed by exactly one `ERROR`
(the terminal outcome, logged once) — matching the spec's distinction
between repeated retry warnings and a single, final DLT error.

### ⚠️ Real Issue Hit — Stale, Non-Compiling Test

While running the full suite to verify all of the above, `audit-service`
failed to even compile its tests:

```java
import com.medinfo.audit.DTO.CreateAuditLogRequestDTO;   // package doesn't exist
import com.medinfo.audit.Enum.AccessMethod;              // package doesn't exist
```

**Root cause:** `AuditServiceTest.java` predated the Kafka migration (Day
5) — it was written against an old REST-based
`createAuditLog(CreateAuditLogRequestDTO)` signature from when audit-service
still exposed `POST /api/audit/log`. That endpoint and its DTO were deleted
when the synchronous Feign audit call was replaced with Kafka, but this test
was never updated to match — it had been silently broken since Day 5 and
only surfaced now because this was the first time `audit-service`'s test
module was included in a full build.

**Fix:** rewrote it against the real current signature,
`createAuditLog(AuditLogEvent)` (from `medinfo-common`), using
`auditRepository.existsByEventId(...)` as the mock setup point since that's
the actual first branch in the method. Three cases now covered:
successful save, duplicate-event-ignored, and repository-failure.

---

## 📌 Chapter 38 — Exception Logging

Both `auth-service` and `medical-service` `GlobalExceptionHandler`s got the
same one-line addition, and only in the generic catch-all:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error occurred. Path={}", request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)...
}
```

Deliberately **not** added to `ResourceNotFoundException`,
`ResourceAlreadyExistsException`, `UnauthorizedException`, or
`ServiceUnavailableException` handlers — those are expected business
outcomes, not failures, and logging them at `ERROR` would drown real
problems in noise every time a client sends a bad request.

**A small SLF4J mechanic worth noting:** the call is
`log.error("...", request.getRequestURI(), ex)` — the message string has
exactly one `{}` placeholder (`Path=`), and `ex` is the *last* argument.
SLF4J's convention: if the final vararg is a `Throwable`, every major
binding (Logback, Log4j2) treats it specially — it prints the full stack
trace after the formatted message, instead of trying to substitute it into
a placeholder. That single line gets both a clean, structured message and a
complete stack trace, without a second `{}` for the exception.

---

## 📌 Chapter 39 — Verification

Full rebuild after all changes:

```bash
mvn -pl medinfo-common,auth-service,medical-service,audit-service,gateway-service -am install -DskipTests
```
This caught both real bugs before they'd have shown up as a runtime 500 on
startup:
1. Gateway's missing Lombok dependency (`LoggingGlobalFilter` compile failure)
2. The `SecurityCofig` filter-ordering exception, in **both** `auth-service`
   and `medical-service`

Full test suite after fixes:

```bash
mvn -pl auth-service,medical-service,audit-service -am test
```

| Service | Tests | Result |
|---|---|---|
| auth-service | 18 | ✅ all passing |
| medical-service | 24 | ✅ all passing |
| audit-service | 4 | ✅ all passing |

**46 tests total**, including the two `@SpringBootTest` full-context tests
(`AuthServiceApplicationTests`, `MedicalServiceApplicationTests`) that
specifically build the real `SecurityFilterChain` bean — the same bean the
filter-ordering bug broke, and the same tests that caught it.

---

## 📌 Design Principles Applied

- **Layered responsibility** — each of the 5 layers logs only what belongs to it; no cross-layer duplication (the Layer 2 controller log was deliberately *not* added where Layers 1 and 3 already covered the same event)
- **Log level as signal, not decoration** — `WARN` reserved for "unexpected but handled correctly" (cache miss, duplicate event, invalid login, retry attempt); `ERROR` reserved for genuine failures (Feign unreachable, DLT, unhandled exceptions)
- **Structured/parameterized logging** — `{}` placeholders throughout, no string concatenation, no `System.out.println`
- **Decorator pattern for cross-cutting concerns** — the DLT logging wrapper adds observability around `DeadLetterPublishingRecoverer` without modifying or duplicating its actual recovery logic
- **Defensive logging in error paths** — `instanceof` pattern matching over hard casts inside the Kafka recoverer, so a malformed record can't crash the very code responsible for recording that failure

---

## 📌 Key Learnings

- Filter ordering in Spring Security is relative and declarative — an anchor
  class must already have a known position before something can be inserted
  "before" it. Custom filters have no implicit position; they only get one
  when explicitly registered, and registration order in code determines
  whether a later `addFilterBefore` call referencing them succeeds or throws
  at context startup.
- This class of bug is invisible to mocked unit tests and only surfaces in
  tests that build the real Spring context — another argument for keeping
  at least one `@SpringBootTest` per service alongside the fast Mockito
  suite.
- Reactive (`GlobalFilter`/`Mono`) and blocking (`OncePerRequestFilter`)
  filters have fundamentally different control flow — code "after"
  `chain.filter(...)` means different things in each, and porting a
  synchronous logging pattern to WebFlux naively would silently log at the
  wrong time.
- `RetryListener` and the terminal recoverer are two different extension
  points in Spring Kafka's `DefaultErrorHandler` — one observes *every*
  failed attempt, the other observes exactly one terminal outcome. Modeling
  "WARN per retry, ERROR once on DLT" required using both, not just wrapping
  the recoverer.
- A test suite is only as trustworthy as its last real compile — the
  audit-service test had been silently broken since the Day 5 Kafka
  migration and nothing caught it until this session explicitly ran a full
  build across every module rather than just the ones being actively edited.

---

## 📌 Work Completed

✅ Designed a 5-layer logging model (Request → Controller → Service → Repository → Infrastructure) with explicit log-level rules
✅ Implemented `RequestLoggingFilter` in `auth-service` and `medical-service` — incoming/completed request logs with method, URL, IP, User-Agent, status, and timing
✅ Implemented `LoggingGlobalFilter` in `gateway-service` — reactive route/path forwarding log
✅ Added business-event logging across `AuthService`, `MedicalProfileService`, `EmergencyContactsService`, `EmergencyService`
✅ Reworded `EmergencyProfileCacheService` logs to match the cache-aside vocabulary (Cached / Evicted)
✅ Replaced a raw `System.out.println` in `AuditEventProducer` with structured logging
✅ Added `Duplicate Audit Event Ignored` (WARN) and `Audit Log Saved` (INFO) to `AuditService`
✅ Added an `ApplicationReadyEvent`-based startup log and a pre-processing "Received" log to `AuditEventConsumer`
✅ Wrapped `DeadLetterPublishingRecoverer` with a logging decorator (ERROR on DLT) and added `RetryListener`-based WARN logging per retry attempt in `KafkaConsumerConfig`
✅ Added `ERROR`-level exception logging (with full stack trace) to both `GlobalExceptionHandler`s, scoped only to unexpected exceptions
✅ Fixed a Spring Security filter-registration-order bug in both `auth-service` and `medical-service`, caught by the existing `@SpringBootTest` integration tests
✅ Fixed a missing-Lombok compile failure in `gateway-service`
✅ Rewrote `audit-service`'s stale, non-compiling `AuditServiceTest` (dead from the Day 5 Kafka migration) against the real current API
✅ Verified full build + 46 passing tests across `auth-service`, `medical-service`, and `audit-service`

---

## 📌 Next Phase

- **DEBUG-level logging** — JWT claims, Redis values, Kafka payloads, Feign
  request/response bodies, SQL parameters — intentionally left out this
  session (normally disabled in production, lower priority than getting
  `INFO`/`WARN`/`ERROR` right first)
- **Cache-hit audit logging** — currently only a cache *miss* publishes a
  Kafka audit event; a cache *hit* is still a real access to someone's
  emergency data and arguably deserves its own audit trail entry
- **No ELK/Loki/Zipkin** — deliberately out of scope. These add real
  operational complexity (running and maintaining a log aggregation stack)
  without teaching much more about *what* to log or *how* to structure it,
  which was the actual goal of this session
