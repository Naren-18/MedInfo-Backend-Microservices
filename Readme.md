# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
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
                    Client
                       │
                       │ JWT
                       ▼

             Medical Service (8082)

        Medical Profile APIs
        Emergency APIs
        Emergency Contacts

                       │
                 OpenFeign Client
                       │
                       ▼

               Auth Service (8081)

       Registration
       Login
       JWT Generation
       Public User API

```

```
Auth Service  →  auth_db   (PostgreSQL)
Medical Service  →  medical_db  (PostgreSQL)
```

> **Next:** API Gateway + Eureka Service Registry will sit in front of both services, removing hardcoded service URLs from the Feign client.

| Service | Owns |
|---|---|
| **Auth Service** | User, Login, Registration, JWT Generation, Spring Security, Public User API |
| **Medical Service** | Medical Profile, Emergency Contacts, Emergency Access Log, Emergency Profile APIs, OpenFeign Client |

**Core principles:**
- One database per service — services never share or cross-query each other's databases
- Only Auth Service generates JWT tokens — every other service validates independently using a shared signing secret
- Services communicate via REST APIs (OpenFeign), not shared databases or shared entities

Each service has:
- ✅ Independent Spring Boot application
- ✅ Independent Maven project
- ✅ Independent PostgreSQL database
- ✅ Independent Security Configuration
- ✅ Independent Deployment
- ✅ Clear ownership of its business domain

---

## 🔐 auth-service

Status: ✅ **Complete** — fully independent and running.

### Structure

```
auth-service
├── config
│      SecurityConfig.java ✅
│
├── controller
│      AuthController.java ✅
│      UserController.java ✅         ← New (Day 2)
│
├── dto
│      LoginRequestDTO.java ✅
│      RegisterRequestDTO.java ✅
│      UserPublicResponseDTO.java ✅  ← New (Day 2)
│
├── entity
│      User.java ✅
│
├── exception
│      GlobalExceptionHandler.java ✅
│      ResourceNotFoundException.java ✅       ← New (Day 2)
│      ResourceAlreadyExistsException.java ✅  ← New (Day 2)
│      UnauthorizedException.java ✅           ← New (Day 2)
│      ServiceUnavailableException.java ✅     ← New (Day 2)
│      ErrorResponse.java ✅                   ← New (Day 2)
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
- **Public User API** — `GET /api/users/public/{publicProfileId}` returns `userId` + `fullName` for downstream services (Day 2)
- Centralized exception handling with custom exceptions and `ErrorResponse` model (Day 2)

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

**Get Public User by Profile ID** *(Day 2 — for inter-service use)*
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

Status: ✅ **Complete** — fully independent, OpenFeign integrated, running.

### Structure

```
medical-service
├── client
│      AuthClient.java ✅             ← New (Day 2)
│
├── config
│      SecurityConfig.java ✅
│      FeignConfig.java ✅            ← New (Day 2)
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
│      UserPublicResponseDTO.java ✅  ← New (Day 2)
│
├── entity
│      MedicalProfile.java ✅
│      EmergencyContacts.java ✅
│      EmergencyAccessLog.java ✅
│
├── exception
│      GlobalExceptionHandler.java ✅
│      ResourceNotFoundException.java ✅       ← New (Day 2)
│      ResourceAlreadyExistsException.java ✅  ← New (Day 2)
│      UnauthorizedException.java ✅           ← New (Day 2)
│      ServiceUnavailableException.java ✅     ← New (Day 2)
│      CustomFeignErrorDecoder.java ✅         ← New (Day 2)
│      ErrorResponse.java ✅                   ← New (Day 2)
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
- Public Emergency Profile API — resolves `publicProfileId` → `userId` via OpenFeign call to Auth Service (Day 2)
- **JWT validation only** — does not generate tokens, uses the same signing secret as Auth Service
- **No direct access to Auth database** — entities store `userId` (Long) instead of a JPA `User` relationship
- **OpenFeign client** (`AuthClient`) for inter-service REST calls (Day 2)
- **Centralized exception framework** with custom exceptions, `ErrorResponse`, and `CustomFeignErrorDecoder` (Day 2)

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
- Spring Cloud OpenFeign (`spring-cloud-starter-openfeign`) ← Added Day 2

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
```

### OpenFeign — AuthClient

```java
@FeignClient(
    name = "auth-service",
    url = "http://localhost:8081"
)
```

Calls `GET /api/users/public/{publicProfileId}` on Auth Service.

Returns `UserPublicResponseDTO { userId, fullName }`.

> ⚠️ The `url` is currently hardcoded. Day 3 will replace this with Eureka Service Discovery.

### Emergency Profile Flow (with OpenFeign)

```
Emergency URL
↓
Medical Service
↓
AuthClient (OpenFeign)
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

Medical Service never touches the auth database.

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
- [ ] Replace hardcoded Feign URL with Eureka Service Discovery
- [ ] Eureka Server
- [ ] API Gateway
- [ ] Spring Cloud Config Server

---

## 📅 Current Status

**Both services are running independently and communicating via OpenFeign.**

One hardcoded URL remains in `AuthClient`:
```java
@FeignClient(name = "auth-service", url = "http://localhost:8081")
```

Next milestone: **Day 3 — Eureka Service Discovery** — register both services with Eureka Server so OpenFeign can resolve service locations dynamically instead of using hardcoded URLs 🚀