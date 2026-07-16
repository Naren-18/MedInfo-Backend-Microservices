# 🧪 Testing MedInfo — End-to-End Guide

This walks through starting the whole system and exercising every API, from
registering a user to scanning an emergency QR code, including verifying the
Kafka event and Redis cache side-effects along the way.

All requests below go through the **API Gateway (`:8080`)** — that's the single
public entry point. Direct service ports (`8081`/`8082`/`8083`) are only used  
for local debugging, not by real clients.

---

## 0. Prerequisites

| Tool | Needed for |
|---|---|
| Java 21 + Maven | Building/running all 5 services |
| Docker | Kafka (via `infrastructure/docker-compose.yml`) |
| Redis | Emergency profile caching (not in docker-compose — run it separately) |
| `curl` or Postman | Sending requests |
| A Postgres client (optional) | Inspecting `auth_db` / `medical_db` / `audit_db` directly |

The databases (`auth_db`, `medical_db`, `audit_db`) are hosted on Neon and
already configured in each service's `application.properties` — no local
Postgres setup needed.

Start Redis locally (any of these work):

```bash
docker run -d --name redis -p 6379:6379 redis:latest
# or, if installed natively:
redis-server
```

---

## 1. Start Infrastructure

**Kafka** (from `infrastructure/`):

```bash
cd infrastructure
docker compose up -d
```

This starts:
- Kafka broker (KRaft mode) on `localhost:9092`
- Kafka UI on `http://localhost:8084` — useful for watching the
  `emergency-access-events` topic live

Verify Kafka is up:

```bash
docker ps   # kafka, kafka-ui both "Up"
```

---

## 2. Start the Services (in this order)

Order matters only for a clean first boot — Eureka clients retry registration,
but starting Eureka first avoids noisy startup logs.

```bash
# 1. Service registry — start first
cd eureka-server        && mvn spring-boot:run

# 2. Business services — any order, each self-registers with Eureka
cd auth-service         && mvn spring-boot:run
cd medical-service      && mvn spring-boot:run
cd audit-service        && mvn spring-boot:run

# 3. Gateway — start last so it has services to route to
cd gateway-service      && mvn spring-boot:run
```

Each service's console should log a line like:
```
o.s.c.n.e.s.EurekaServiceRegistry : Registering application AUTH-SERVICE with eureka with status UP
```

### Verify everyone registered

Open the Eureka dashboard: **http://localhost:8761**

You should see 4 registered instances:

| Application | Status |
|---|---|
| AUTH-SERVICE | UP |
| MEDICAL-SERVICE | UP |
| AUDIT-SERVICE | UP |
| GATEWAY-SERVICE | UP |

(`AUDIT-SERVICE` is a Kafka consumer with no REST API — it still registers
with Eureka, it's just never called by the Gateway or Feign.)

> ⚠️ You may see a red **"EMERGENCY! Eureka may be incorrectly claiming
> instances are up"** banner locally. This is Eureka's Self Preservation Mode
> reacting to low heartbeat volume with only a handful of instances — expected
> in local dev, not a bug, no action needed.

---

## 3. End-to-End API Walkthrough

All examples use `curl`; swap in Postman if you prefer (see `postman/` for a
ready-made collection/environment).

### 3.1 Register a user

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
        "fullName": "Narendra Kumar",
        "email": "narendra@example.com",
        "password": "password123"
      }'
```

Expected: `200 OK`, body `"User Registration Completed"`.

Try it again with the same email — expect `409 Conflict`:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "User already exists with email : narendra@example.com"
}
```

### 3.2 Log in

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
        "email": "narendra@example.com",
        "password": "password123"
      }'
```

Expected: `200 OK`, body is a raw JWT string. Save it:

```bash
TOKEN="<paste the returned token>"
```

The JWT contains `userId` and `role` as custom claims — every downstream
service validates it independently (same shared `jwt.secret`), no database
lookup required. It expires in 15 minutes (`jwt.expiration=900000`ms).

Negative cases:
- Wrong password → `401 Unauthorized`, `"Password Invalid"`
- Unknown email → `401 Unauthorized`, `"Invalid Credentials"`

### 3.3 Create your medical profile

```bash
curl -X POST http://localhost:8080/api/profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "age": 28,
        "gender": "Male",
        "bloodGroup": "O+",
        "height": 175.0,
        "weight": 70.0,
        "allergies": "None",
        "medicalConditions": "None",
        "currentMedications": "None",
        "organDonor": true
      }'
```

Expected: `200 OK`, returns the saved `MedicalProfile` — note the
`publicProfileId` (a UUID) in the response. **Save it** — this is the
identifier used for the emergency QR flow:

```bash
PUBLIC_PROFILE_ID="<publicProfileId from the response>"
```

Try creating a second profile for the same user → `409 Conflict`
(`ResourceAlreadyExistsException`, one profile per `userId`).

### 3.4 Read / update your own profile

```bash
# Read
curl http://localhost:8080/api/profile -H "Authorization: Bearer $TOKEN"

# Update
curl -X PUT http://localhost:8080/api/profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "age": 29, "gender": "Male", "bloodGroup": "O+",
        "height": 175.0, "weight": 72.0,
        "allergies": "Penicillin", "medicalConditions": "None",
        "currentMedications": "None", "organDonor": true
      }'
```

Every `PUT` evicts the Redis cache entry for this profile's
`publicProfileId` (see §3.7) — so a stale cached emergency response never
survives an update.

### 3.5 Add emergency contacts

```bash
curl -X POST http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Asha Kumar",
        "relationship": "Spouse",
        "phoneNumber": "9876543210"
      }'
```

```bash
# List your contacts
curl http://localhost:8080/api/contacts -H "Authorization: Bearer $TOKEN"
```

Save the returned contact `id` if you want to test update/delete:

```bash
curl -X PUT http://localhost:8080/api/contacts/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Asha Kumar","relationship":"Spouse","phoneNumber":"9876543211"}'

curl -X DELETE http://localhost:8080/api/contacts/1 -H "Authorization: Bearer $TOKEN"
```

Cross-user ownership is enforced — attempting to update/delete a contact
belonging to a different `userId` returns `401 Unauthorized`.

### 3.6 Scan the emergency QR code (no login required)

This is the public, unauthenticated endpoint a first responder would hit:

```bash
curl http://localhost:8080/api/emergency/$PUBLIC_PROFILE_ID
```

Expected: `200 OK` with `fullName`, `bloodGroup`, `allergies`,
`medicalConditions`, `currentMedications`, `organDonor`, and the
`emergencyContacts` list.

What happens behind this one call:
1. **Cache check** — Redis key `emergency-profile::<publicProfileId>` (miss, first time)
2. `MedicalProfile` is resolved **locally** by `publicProfileId` (no Auth
   lookup needed for this step — Medical Service owns this identifier)
3. **Feign call to Auth Service** (`GET /api/auth/internal/users/{userId}`)
   to resolve `fullName` — the one piece of data Medical doesn't own
4. Emergency contacts are loaded
5. A **Kafka event** (`emergency-access-events`) is published, fire-and-forget
   — the response does **not** wait on this
6. The assembled response is cached in Redis (TTL 10 minutes) and returned

Run the same `curl` again — this time it's a **cache hit**: no DB query, no
Feign call, no Kafka event, just Redis. You'll see this difference directly
in the medical-service console log:
```
Cache MISS for <publicProfileId>   # first call
Cache HIT for <publicProfileId>    # second call
```

Try an unknown `publicProfileId` → `404 Not Found`.

### 3.7 Verify the Redis cache directly

```bash
redis-cli KEYS "emergency-profile::*"
redis-cli GET "emergency-profile::$PUBLIC_PROFILE_ID"
redis-cli TTL "emergency-profile::$PUBLIC_PROFILE_ID"
```

Now update the profile again (§3.4) and re-check:

```bash
redis-cli GET "emergency-profile::$PUBLIC_PROFILE_ID"   # should be empty — evicted
```

The next `curl /api/emergency/$PUBLIC_PROFILE_ID` call will be a cache MISS
again and repopulate it with the fresh data.

### 3.8 Verify the Kafka event and audit trail

**Option A — Kafka UI**: open http://localhost:8084, browse to the
`emergency-access-events` topic, and you'll see one JSON message per
cache-miss emergency access (`userId`, `ipAddress`, `userAgent`,
`accessMethod`, `accessedAt`, `eventId`).

**Option B — console consumer**:
```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic emergency-access-events \
  --from-beginning
```

**Confirm it was persisted** by audit-service — check `audit_db.audit_logs`
(via your Postgres client) or watch the audit-service console for:
```
c.m.audit.consumer.AuditEventConsumer : consumed event ...
```

Each row's `eventId` is unique — replaying the same event (e.g. by
re-publishing manually) is safely ignored (`existsByEventId` idempotency
check), so duplicate Kafka delivery never creates duplicate audit rows.

### 3.9 Simulate failures (optional but instructive)

**Auth Service down** — stop `auth-service`, then hit a fresh (uncached)
emergency profile:
```bash
curl -i http://localhost:8080/api/emergency/$PUBLIC_PROFILE_ID
```
Expected: `503 Service Unavailable`, `"Auth Service is not available"` — not
a raw connection-refused error. This is `EmergencyService` catching Feign's
`RetryableException` and translating it via `ServiceUnavailableException`.
Restart `auth-service` and it recovers on the next request — no code/config
changes needed (Eureka + Feign resolve the new instance automatically).

**Audit Service down** — stop `audit-service`, then hit a fresh emergency
profile. The `200 OK` response still returns immediately (Kafka
decouples this) — the event just sits in the topic until `audit-service`
comes back and consumes it. Restart `audit-service` and confirm the
buffered event lands in `audit_db` shortly after.

**Redis down** — stop Redis, then hit `/api/emergency/{id}`. Expect this to
currently fail loudly (no fallback-to-DB-only path is implemented yet) —
worth knowing as a gap, not a bug you introduced.

---

## 4. Running the Automated Test Suite

Each service (except `eureka-server` / `gateway-service`) has JUnit 5 +
Mockito unit tests with JaCoCo coverage:

```bash
# All services with tests, from repo root
mvn -pl auth-service,medical-service,audit-service -am test

# Single service
cd medical-service && mvn test
```

Coverage reports land at:
```
<service>/target/site/jacoco/index.html
```

These are **pure unit tests** — no Spring context, no real Postgres, no
Kafka, no Redis, no Eureka. Repositories, Feign clients, the Redis cache
service, and `SecurityContextHolder` are all mocked with Mockito, so they run
in milliseconds and don't require any of the infrastructure from §1–2.

---

## 5. Quick Reference — All Endpoints (through the Gateway)

| Method | Path | Auth required | Service |
|---|---|---|---|
| POST | `/api/auth/register` | No | Auth |
| POST | `/api/auth/login` | No | Auth |
| GET | `/api/auth/internal/users/{userId}` | No (internal, service-to-service only) | Auth |
| POST | `/api/profile` | Yes | Medical |
| GET | `/api/profile` | Yes | Medical |
| PUT | `/api/profile` | Yes | Medical |
| DELETE | `/api/profile` | Yes | Medical |
| POST | `/api/contacts` | Yes | Medical |
| GET | `/api/contacts` | Yes | Medical |
| PUT | `/api/contacts/{id}` | Yes | Medical |
| DELETE | `/api/contacts/{id}` | Yes | Medical |
| GET | `/api/emergency/{publicProfileId}` | No (public QR flow) | Medical |

`Authorization: Bearer <token>` from §3.2 is required on every "Yes" row.

---

## 6. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `UnknownHostException` at the Gateway | Eureka registered a service under a corporate hostname it can't resolve | Confirm `eureka.instance.prefer-ip-address=true` is set in that service's `application.properties` |
| `404` calling `/api/profile/**` through the Gateway | Gateway route predicate didn't include `/api/profile/**` | Fixed as part of this doc's setup — check `gateway-service/application.properties` routes[1] predicate |
| `503 Service Unavailable` on `/api/emergency/**` | Auth Service isn't running / isn't registered with Eureka yet | Start/check `auth-service`, wait for its Eureka heartbeat |
| Emergency profile never updates after a `PUT /api/profile` | Redis eviction didn't fire, or you're hitting a differently-cased key | Check `redis-cli KEYS "emergency-profile::*"` — key is `emergency-profile::<publicProfileId>`, evicted in `MedicalProfileService.updateProfile()` |
| App fails to start with `column "public_profile_id" ... contains null values` | Pre-existing rows in `medical_profile` predate the `publicProfileId` column and are `NULL` | Backfill those rows (assign a UUID) or drop/recreate the table in that environment before `ddl-auto=update` can add the `NOT NULL` constraint |
| Kafka events not appearing | `infrastructure` docker-compose not running, or wrong bootstrap server | `docker ps` to confirm `kafka` container is up on `9092`; check `spring.kafka.bootstrap-servers=localhost:9092` in both `medical-service` and `audit-service` |
