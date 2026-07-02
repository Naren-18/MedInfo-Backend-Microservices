# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
├── eureka-server       # Service Registry (Netflix Eureka)
├── auth-service        # Authentication & user identity
├── medical-service     # Medical profiles, contacts, emergency access
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
                    Eureka Server (8761)
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼

    AUTH-SERVICE (8081)          MEDICAL-SERVICE (8082)
         ▲                               │
         │                               │
         └────────────OpenFeign──────────┘
```

```
Client
↓
Medical Service
↓
OpenFeign
↓
Eureka Server  →  "Where is AUTH-SERVICE?"
↓
Authentication Service
↓
User Details
↓
Medical Service → Medical Profile → Emergency Contacts
↓
EmergencyProfileResponseDTO
↓
Client
```

```
Auth Service     →  auth_db     (PostgreSQL)
Medical Service  →  medical_db  (PostgreSQL)
```

> **Next:** API Gateway — single public entry point in front of both services, integrated with Eureka.

| Service | Port | Owns |
|---|---|---|
| **Eureka Server** | 8761 | Service Registry, Heartbeats, Dashboard |
| **Auth Service** | 8081 | User, Login, Registration, JWT Generation, Spring Security, Public User API |
| **Medical Service** | 8082 | Medical Profile, Emergency Contacts, Emergency Access Log, Emergency Profile APIs, OpenFeign Client |

**Core principles:**
- One database per service — services never share or cross-query each other's databases
- Only Auth Service generates JWT tokens — every other service validates independently using a shared signing secret
- Services communicate via REST APIs (OpenFeign), not shared databases or shared entities
- **Services locate each other by logical name through Eureka — no hardcoded URLs anywhere** (Day 3)

Each service has:
- ✅ Independent Spring Boot application
- ✅ Independent Maven project
- ✅ Independent PostgreSQL database
- ✅ Independent Security Configuration
- ✅ Independent Deployment
- ✅ Clear ownership of its business domain
- ✅ Registered with Eureka Service Registry

---

## 🧭 eureka-server

Status: ✅ **Complete** — running with both services registered.

### What it does
- Central **Service Registry** — every microservice registers itself at startup
- Stores service name, host, port, status, and health information
- Receives periodic **heartbeats** from registered services
- Answers discovery queries: *"Where is AUTH-SERVICE?"* → current address
- Dashboard at `http://localhost:8761`

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

> ℹ️ **Self Preservation Mode:** In local development the dashboard may show an "EMERGENCY!" warning. This is expected — Eureka avoids evicting instances when heartbeat traffic is low. In production with many services this disappears automatically. No configuration change required.

---

## 🔐 auth-service

Status: ✅ **Complete** — fully independent, registered with Eureka.

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
- **Eureka Client** — registers as `AUTH-SERVICE` and sends heartbeats (Day 3)

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
- Spring Cloud Netflix Eureka Client ← Added Day 3

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

# Eureka (Day 3)
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
```

⚠️ Never commit real credentials to Git. Use environment variables in production.

### APIs

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

Status: ✅ **Complete** — fully independent, OpenFeign + Eureka integrated.

### Structure

```
medical-service
├── client
│      AuthClient.java ✅
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
│
├── entity
│      MedicalProfile.java ✅
│      EmergencyContacts.java ✅
│      EmergencyAccessLog.java ✅
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
│      EmergencyAccessLogRepository.java ✅
│
├── security
│      JWTAuthenticationFilter.java ✅
│      JWTService.java ✅
│
├── service
│      MedicalProfileService.java ✅
│      EmergencyContactsService.java ✅
│      EmergencyService.java ✅
│      EmergencyAccessLogService.java ✅
│
└── MedicalServiceApplication.java ✅
```

### Responsibilities
- Medical Profile CRUD
- Emergency Contacts CRUD
- Emergency Access Logging
- Public Emergency Profile API — resolves `publicProfileId` → `userId` via OpenFeign call to Auth Service
- **JWT validation only** — does not generate tokens, uses the same signing secret as Auth Service
- **No direct access to Auth database** — entities store `userId` (Long) instead of a JPA `User` relationship
- **OpenFeign client** (`AuthClient`) — now resolves auth-service **by name through Eureka** (Day 3)
- **Centralized exception framework** with custom exceptions, `ErrorResponse`, and `CustomFeignErrorDecoder`
- **Eureka Client** — registers as `MEDICAL-SERVICE` and sends heartbeats (Day 3)

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
- Spring Cloud Netflix Eureka Client ← Added Day 3

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

# Eureka (Day 3)
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
```

### OpenFeign — AuthClient (now with Service Discovery)

**Before (Day 2 — hardcoded):**
```java
@FeignClient(
    name = "auth-service",
    url = "http://localhost:8081"
)
```

**After (Day 3 — resolved via Eureka):**
```java
@FeignClient(
    name = "auth-service"
)
```

Feign asks Eureka *"Where is AUTH-SERVICE?"* before making the HTTP call. If Auth Service changes host, container, or port, Eureka provides the updated address — **no code changes, no redeployment of consumers**.

### Emergency Profile Flow (with Eureka)

```
Emergency URL
↓
Medical Service
↓
AuthClient (OpenFeign)
↓
Eureka Server → resolves AUTH-SERVICE → localhost:8081
↓
Auth Service → UserRepository
↓
UserPublicResponseDTO { userId, fullName }
↓
Medical Service → MedicalProfileRepository
↓
Medical Service → EmergencyContactsRepository
↓
EmergencyProfileResponseDTO
```

Medical Service never touches the auth database — and never knows its address.

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
Applied to `MedicalProfile`, `EmergencyContacts`, `EmergencyAccessLog`.
Repository methods updated: `findByUser(User)` → `findByUserId(Long)`.

**JWT Authentication — no database lookup:**
```
// Before (Monolith)
JWT → Extract Email → UserRepository → UserDetails → Authentication

// After (Medical Service)
JWT → Validate Signature → Extract userId → SecurityContextHolder
```
```java
UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(userId, null, null);
```

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

## 🧠 Principles Learned

- **Migrating to microservices is not just moving Java classes.** Each service needs its own source code, dependencies, configuration, database, security setup, and `pom.xml`.
- **Migrate bottom-up:** DTO → Entity → Repository → Service → Controller → Security → Exception. This order minimizes compilation errors.
- **Business logic can stay the same while ownership changes.** Most Java code is unchanged — what changed is database, configuration, and deployment independence.
- **Cross-service JPA relationships are impossible.** Replace with a plain `userId` reference — never duplicate entities, never cross-query databases.
- **Shared JWT secret enables decentralized authentication.** Every service verifies tokens independently — no token-introspection call to Auth Service.
- **Custom JWT claims avoid unnecessary database calls.** `userId` and `role` embedded in the token mean downstream services can authenticate with zero DB lookups.
- **Each service owns its data. Others access it through APIs, never through the database.** `EmergencyService` calls Auth Service via OpenFeign to resolve `publicProfileId` → `userId` instead of querying `auth_db`.
- **Feign Error Decoder only handles HTTP responses.** Connection failures (service offline) produce a `RetryableException`, not an HTTP response — handle both separately. Long-term solution: Resilience4j Circuit Breakers.
- **Hardcoded service URLs don't survive real environments.** Services scale, restart, and move — Service Discovery lets consumers resolve providers by logical name, with zero code changes when locations change. (Day 3)
- **Eureka's Self Preservation Mode is a feature, not a bug.** Low heartbeat traffic in local dev triggers the "EMERGENCY!" warning — Eureka is refusing to evict possibly-healthy instances. Expected locally, disappears in production. (Day 3)

---

## ✅ Progress

- [x] Planned the microservices architecture
- [x] Designed clear service boundaries
- [x] Created two independent Spring Boot projects
- [x] Configured separate PostgreSQL databases (`auth_db`, `medical_db`)
- [x] Migrated the complete Authentication domain
- [x] Resolved dependency, configuration, and database migration issues
- [x] Successfully launched auth-service independently
- [x] Created medical-service with its own independent database
- [x] Migrated the complete Medical domain (DTOs, Entities, Repositories, Services, Controllers)
- [x] Redesigned domain model — replaced JPA `User` relationships with `userId` references
- [x] Redesigned JWT to include custom claims (`userId`, `role`)
- [x] Implemented independent JWT validation in Medical Service (shared signing secret)
- [x] Redesigned Spring Security for Medical Service — `userId`-based auth, no DB lookup
- [x] Successfully launched medical-service independently
- [x] Introduced Public User API in Auth Service (`GET /api/users/public/{publicProfileId}`)
- [x] Implemented OpenFeign in Medical Service (`AuthClient`)
- [x] Removed cross-service database dependency — EmergencyService uses OpenFeign, not UserRepository
- [x] Implemented centralized exception framework (custom exceptions + `ErrorResponse`)
- [x] Introduced `CustomFeignErrorDecoder` via `FeignConfig`
- [x] Implemented graceful handling of downstream service failures (503 response)
- [x] Created Eureka Server (port 8761) — Day 3
- [x] Registered auth-service as Eureka Client — Day 3
- [x] Registered medical-service as Eureka Client — Day 3
- [x] Removed hardcoded Feign URL — service resolved by logical name via Eureka — Day 3
- [x] Verified heartbeats and registration on Eureka Dashboard — Day 3
- [x] Validated end-to-end communication through service discovery — Day 3
- [ ] API Gateway (routing, single entry point, Eureka-integrated)
- [ ] Spring Cloud Config Server

---

## 📅 Current Status

**Three applications running: Eureka Server + two microservices, communicating through dynamic service discovery.** No hardcoded service addresses anywhere.

One gap remains: the client still calls each microservice directly on its own port.

```
Client → Medical Service (8082)
Client → Auth Service (8081)
```

Next milestone: **Day 4 — API Gateway** — a single public entry point that routes requests through Eureka to the right service, providing centralized routing, a foundation for gateway-level JWT validation, centralized CORS handling, and future rate limiting 🚀