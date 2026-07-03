# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
├── eureka-server       # Service Registry (Netflix Eureka)
├── gateway-service     # API Gateway (Spring Cloud Gateway)
├── auth-service        # Authentication & user identity
├── medical-service     # Medical profiles, contacts, emergency access
├── audit-service       # Centralized audit logging
├── postman
└── README.md
```

**Why a Monorepo?**
- Easier local development
- Simpler GitHub management
- Easier CI/CD during learning
- Common industry approach for medium-sized projects

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
     (8081)          (8082)           (8083)
        ▲              │  │              ▲
        │              │  │              │
        └────Feign─────┘  └────Feign─────┘

   auth_db         medical_db        audit_db

                 Eureka Server (8761)
```

**Gateway Request Lifecycle:**
```
Client → API Gateway → Route Matching → Eureka Service Discovery
       → Target Microservice → Business Logic → Response → Client
```

| Service | Port | Owns |
|---|---|---|
| **API Gateway** | 8080 | Single public entry point, dynamic routing, Eureka-integrated load balancing |
| **Eureka Server** | 8761 | Service Registry, Heartbeats, Dashboard |
| **Auth Service** | 8081 | User, Login, Registration, JWT Generation, Spring Security, Public User API |
| **Medical Service** | 8082 | Medical Profile, Emergency Contacts, Emergency Profile APIs, Feign Clients (Auth + Audit) |
| **Audit Service** | 8083 | Centralized audit logging — single source of truth for application auditing |

**Core principles:**
- One database per service — services never share or cross-query each other's databases
- Only Auth Service generates JWT tokens — every other service validates independently using a shared signing secret
- Services communicate via REST APIs (OpenFeign), not shared databases or shared entities
- Services locate each other by logical name through Eureka — no hardcoded URLs anywhere
- Clients communicate with ONE endpoint — the Gateway routes everything
- Each business capability lives in its own bounded context — audit logging is not a medical concern (Day 4)
- **Business logic is unit tested in isolation — no database, no HTTP, no Spring context** (Day 4)

Each service has:
- ✅ Independent Spring Boot application
- ✅ Independent Maven project
- ✅ Independent PostgreSQL database (business services)
- ✅ Independent Deployment
- ✅ Clear ownership of its business domain
- ✅ Registered with Eureka Service Registry
- ✅ JUnit 5 + Mockito unit test suite with JaCoCo coverage (business services)

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
- Audit Service (Feign)
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
Medical Service → Mock Audit Client → Success Response
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
spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/medical/**,/api/contacts/**,/api/emergency/**
```

### Route Table

| Path Predicates | Target |
|---|---|
| `/api/auth/**`, `/api/users/**` | `lb://AUTH-SERVICE` |
| `/api/medical/**`, `/api/contacts/**`, `/api/emergency/**` | `lb://MEDICAL-SERVICE` |

> 💡 The `lb://` prefix tells Spring Cloud Gateway to use the LoadBalancer + Eureka to discover the destination dynamically — no hardcoded hosts or ports.

> ℹ️ The Audit Service has no Gateway route — it is an **internal service** that only receives traffic from other backend services via Feign.

### ⚠️ Real Issue Hit — UnknownHostException

Gateway requests initially failed because Eureka registered services under the machine's **corporate hostname** (`HSC-XXXX.allegisgroup.com`), which couldn't be resolved locally. Fix: set `eureka.instance.prefer-ip-address=true` on every service so Eureka registers IP addresses instead of hostnames.

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

```properties
spring.application.name=eureka-server
server.port=8761

# The server itself is not a client
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

> ℹ️ **Self Preservation Mode:** In local development the dashboard may show an "EMERGENCY!" warning. This is expected — Eureka avoids evicting instances when heartbeat traffic is low. In production with many services this disappears automatically.

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
│      AuthController.java ✅
│      UserController.java ✅
│
├── dto
│      LoginRequestDTO.java ✅
│      RegisterRequestDTO.java ✅
│      UserPublicResponseDTO.java ✅
│
├── entity
│      User.java ✅
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
│      AuthServiceTest.java ✅              ← New (Day 4)
│      JWTServiceTest.java ✅               ← New (Day 4)
│      CustomUserDetailsServiceTest.java ✅ ← New (Day 4)
│
└── AuthServiceApplication.java ✅
```

### Responsibilities
- User registration with UUID-based public profile ID
- Login with BCrypt password verification
- JWT generation — includes **custom claims** (`userId`, `role`) so downstream services authenticate without a database lookup
- JWT validation via `JWTAuthenticationFilter` (runs on every request)
- `CustomUserDetailsService` — loads user from DB for Spring Security
- **Public User API** — `GET /api/users/public/{publicProfileId}` returns `userId` + `fullName` for downstream services
- Centralized exception handling with custom exceptions and `ErrorResponse` model
- **Eureka Client** — registers as `AUTH-SERVICE` and sends heartbeats
- **Fully unit tested** — registration, login, JWT lifecycle, user lookup (Day 4)

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
- Spring Boot Test + Mockito ← Day 4

### Configuration

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

⚠️ Never commit real credentials to Git. Use environment variables in production.

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

**Get Public User by Profile ID** *(inter-service use)*
```
GET /api/users/public/{publicProfileId}
Authorization: Bearer <jwt_token>
```
Response:
```json
{
  "userId": 1,
  "fullName": "Narendra Kumar"
}
```

---

## 🩺 medical-service

Status: ✅ **Complete** — pure medical domain, audit logging delegated to Audit Service, unit tested.

### Structure

```
medical-service
├── client
│      AuthClient.java ✅
│      AuditClient.java ✅             ← New (Day 4)
│
├── config
│      SecurityConfig.java ✅
│      FeignConfig.java ✅
│
├── controller
│      EmergencyController.java ✅
│      EmergencyContactsController.java ✅
│      MedicalProfileController.java ✅
│
├── dto
│      CreateMedicalProfileDTO.java ✅
│      MedicalProfileResponseDTO.java ✅
│      EmergencyProfileResponseDTO.java ✅
│      EContactsDTO.java ✅
│      UserPublicResponseDTO.java ✅
│      CreateAuditLogRequestDTO.java ✅  ← New (Day 4)
│
├── entity
│      MedicalProfile.java ✅
│      EmergencyContacts.java ✅
│      (EmergencyAccessLog — REMOVED, moved to audit-service) ← Day 4
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
│      (EmergencyAccessLogRepository — REMOVED) ← Day 4
│
├── security
│      JWTAuthenticationFilter.java ✅
│      JWTService.java ✅
│
├── service
│      MedicalProfileService.java ✅
│      EmergencyContactsService.java ✅
│      EmergencyService.java ✅
│      (EmergencyAccessLogService — REMOVED) ← Day 4
│
├── test
│      MedicalProfileServiceTest.java ✅     ← New (Day 4)
│      EmergencyContactsServiceTest.java ✅  ← New (Day 4)
│      EmergencyServiceTest.java ✅          ← New (Day 4)
│
└── MedicalServiceApplication.java ✅
```

### Responsibilities
- Medical Profile CRUD
- Emergency Contacts CRUD
- Public Emergency Profile API — resolves `publicProfileId` → `userId` via OpenFeign call to Auth Service
- **Audit delegation** — sends `CreateAuditLogRequestDTO` to Audit Service via `AuditClient` on every emergency access (Day 4)
- **JWT validation only** — does not generate tokens, uses the same signing secret as Auth Service
- **No direct access to other services' databases** — `userId` references + Feign calls only
- **Two OpenFeign clients** — `AuthClient` (user resolution) and `AuditClient` (audit logging), both resolved by name through Eureka
- **Centralized exception framework** with custom exceptions, `ErrorResponse`, and `CustomFeignErrorDecoder`
- **Eureka Client** — registers as `MEDICAL-SERVICE`
- **Fully unit tested** — CRUD paths, ownership validation across users, mocked Feign clients including downstream-unavailable, mocked SecurityContext (Day 4)

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
- Spring Boot Test + Mockito ← Day 4

**Database:** `medical_db` · **Port:** `8082`

### Configuration

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
```

### OpenFeign Clients

```java
@FeignClient(name = "auth-service")   // resolves publicProfileId → userId
@FeignClient(name = "audit-service")  // persists audit logs (Day 4)
```

No URLs — both resolved by logical name through Eureka. If either service changes host, container, or port, Eureka provides the updated address — **no code changes, no redeployment of consumers**.

### Emergency Profile Flow (Gateway + Eureka + 2 Feign calls)

```
Client
↓
API Gateway (8080) → route match /api/emergency/**
↓
Eureka → MEDICAL-SERVICE
↓
Medical Service
↓
AuthClient (OpenFeign) → Eureka → AUTH-SERVICE
↓
UserPublicResponseDTO { userId, fullName }
↓
Medical Service → MedicalProfileRepository → EmergencyContactsRepository
↓
AuditClient (OpenFeign) → Eureka → AUDIT-SERVICE → audit_db   ← Day 4
↓
EmergencyProfileResponseDTO
↓
Client
```

Medical Service touches only `medical_db` — user data comes from Auth Service, audit records go to Audit Service.

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

**Audit Ownership — extracted to Audit Service (Day 4):**
```
// Before (Day 3)
Emergency Profile Viewed → EmergencyAccessLogService → medical_db

// After (Day 4)
Emergency Profile Viewed → AuditClient (Feign) → Audit Service → audit_db
```
`EmergencyAccessLog` entity, repository, and service **removed** from medical-service entirely.

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

Status: ✅ **Complete** — fourth microservice, single source of truth for auditing, unit tested.

### What it does
- Receives audit events from other backend services via REST
- Persists every emergency profile access: who, from where, with what client, how, and when
- Designed **generically** (`AuditLog`, not `EmergencyAccessLog`) so future events — user login, profile updates, contact modifications, password changes — land in the same service
- **Internal-only service**: no Spring Security, no Gateway route — receives requests only from other backend services
- **Unit tested** — successful audit log creation + repository failure (Day 4)

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
- Spring Boot Test

> 💡 Spring Security intentionally **not** added — this is an internal microservice.

**Database:** `audit_db` · **Port:** `8083`

### Configuration

```properties
spring.application.name=audit-service
server.port=8083

eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
eureka.instance.prefer-ip-address=true
```

### Domain — AuditLog

| Field | Purpose |
|---|---|
| id | Primary key |
| userId | Which user's data was accessed |
| ipAddress | Where the request came from |
| userAgent | What client made the request |
| accessMethod | `URL` or `QR_CODE` (enum) |
| accessedAt | Auto-generated via `@CreationTimestamp` |

### API

**Log an audit event** *(called by other services via Feign)*
```
POST /api/audit/log
```
```json
{
  "userId": 1,
  "ipAddress": "127.0.0.1",
  "userAgent": "Mozilla/5.0",
  "accessMethod": "URL"
}
```

Flow: `AuditController` → `AuditService` → DTO converted to `AuditLog` entity → `audit_db`.

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
- **A capability that isn't part of your domain belongs in its own service.** Audit logging worked inside Medical Service but violated bounded context — extraction gave it its own database, its own scaling, and made it reusable for future event types. (Day 4)
- **Design extracted domains generically.** Renaming `EmergencyAccessLog` → `AuditLog` turned a single-purpose table into a platform capability that can absorb login events, profile updates, and password changes without redesign. (Day 4)
- **Internal services don't automatically need Spring Security.** The Audit Service receives traffic only from backend services — deliberate omission, not oversight. (Day 4)
- **Unit tests isolate business logic from infrastructure.** Mock the repository, mock the Feign clients, mock the SecurityContext — tests run in milliseconds with no PostgreSQL, no HTTP, no Spring Boot server. (Day 4)
- **Test the failure paths, not just the happy path.** Duplicates, missing resources, invalid credentials, unauthorized access across users, downstream service unavailability, repository exceptions — failures are where production bugs live. (Day 4)
- **`@Value` fields need reflection in plain unit tests.** Spring's property injection doesn't run without a Spring context — inject `secretKey`/`jwtExpiration` manually via reflection. (Day 4)
- **Coverage reports show what you *haven't* tested.** JaCoCo's branch/line breakdown turns "I think it's tested" into "I know what's missing." (Day 4)

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
- [x] Created Audit Service with dedicated `audit_db` — Day 4
- [x] Designed generic `AuditLog` domain (URL / QR_CODE access methods) — Day 4
- [x] Created Audit REST API (`POST /api/audit/log`) and tested independently — Day 4
- [x] Implemented `AuditClient` Feign client in Medical Service — Day 4
- [x] Migrated audit logging from Medical Service to Audit Service — Day 4
- [x] Removed `EmergencyAccessLog` entity, repository, and service from Medical Service — Day 4
- [x] Introduced JUnit 5 for unit testing — Day 4
- [x] Implemented Mockito-based dependency mocking — Day 4
- [x] Tested Authentication Service (AuthServiceTest) — Day 4
- [x] Tested JWT Service (JWTServiceTest, `@Value` fields via reflection) — Day 4
- [x] Tested CustomUserDetailsService — Day 4
- [x] Tested MedicalProfileService (mocked SecurityContextHolder) — Day 4
- [x] Tested EmergencyContactsService (ownership validation across users) — Day 4
- [x] Tested EmergencyService (mocked Auth + Audit Feign clients, downstream failure) — Day 4
- [x] Tested AuditService (creation + repository failure) — Day 4
- [x] Tested success and failure scenarios across all services — Day 4
- [x] Integrated JaCoCo for code coverage in all three business services — Day 4
- [x] Generated HTML coverage reports (`target/site/jacoco/index.html`) — Day 4
- [ ] Kafka for asynchronous audit event processing (Day 5)
- [ ] Redis Caching
- [ ] Docker & Docker Compose
- [ ] CI/CD with GitHub Actions
- [ ] Cloud Deployment

---

## 📅 Current Status

**Five applications running: API Gateway + Eureka Server + three business microservices** (auth, medical, audit), each with its own database, communicating exclusively through Eureka-resolved Feign calls — **and all three business services fully unit tested with JaCoCo coverage reporting.**

```
Client → Gateway → Eureka → { AUTH, MEDICAL, AUDIT }
Medical → Feign → Auth   (user resolution)
Medical → Feign → Audit  (audit logging)

mvn clean test → JaCoCo HTML report per service
```

Bounded contexts are clean: Medical Service owns only medical data; Audit Service is the single source of truth for auditing.

One architectural improvement queued: the Medical → Audit Feign call is **synchronous** — audit persistence blocks the emergency profile response, and the two services remain tightly coupled at runtime.

Next milestone: **Day 5 — Event-Driven Architecture with Apache Kafka** — Kafka fundamentals, producers and consumers, topics and partitions, consumer groups, event design, publishing emergency access events, consuming them in Audit Service, and removing the synchronous Feign audit communication entirely 🚀