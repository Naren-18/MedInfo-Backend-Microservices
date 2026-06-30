# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
├── auth-service        # Authentication & user identity
├── medical-service      # Medical profiles, contacts, emergency access
├── postman
└── README.md
```

**Why a Monorepo?**
- Easier local development
- Simpler GitHub management
- Easier CI/CD during learning
- Common industry approach for medium-sized projects

---

## 🏗️ Target Architecture

```
                Client
                   │
                   ▼
             API Gateway
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
   Auth Service        Medical Service
```

```
                 Client
                    │
                    ▼
        ┌─────────────────────┐
        │    Auth Service     │
        │---------------------│
        │ User                │
        │ Login               │
        │ Registration        │
        │ JWT Generation      │
        └─────────────────────┘
                 │
           JWT Token
                 │
                 ▼
        ┌─────────────────────┐
        │   Medical Service   │
        │---------------------│
        │ Medical Profile     │
        │ Emergency Contacts  │
        │ Emergency Logs      │
        │ JWT Validation      │
        └─────────────────────┘
```

| Service | Owns |
|---|---|
| **Auth Service** | User, Login, Registration, JWT Generation, Spring Security |
| **Medical Service** | Medical Profile, Emergency Contacts, Emergency Access Log, Emergency Profile APIs |

**Core principle — one database per service:**
```
Auth Service
      │
   auth_db

Medical Service
      │
 medical_db
```

**JWT Strategy:**
- Only **Auth Service** generates JWT tokens.
- Every microservice (including Medical Service) validates JWTs independently.
- This prevents unauthorized access even if someone bypasses the API Gateway.

Each service now has:
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
│
├── dto
│      LoginRequestDTO.java ✅
│      RegisterRequestDTO.java ✅
│
├── entity
│      User.java ✅
│
├── exception
│      GlobalExceptionHandler.java ✅
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
- JWT generation — now includes **custom claims** (`userId`, `role`), not just email, so downstream services can authenticate without a database lookup
- JWT validation via `JWTAuthenticationFilter` (runs on every request)
- `CustomUserDetailsService` — loads user from DB for Spring Security
- Centralized exception handling via `@RestControllerAdvice`

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

JWT payload now contains custom claims:
```json
{
  "sub": "admin@gmail.com",
  "userId": 1,
  "role": "USER"
}
```

---

## 🩺 medical-service

Status: ✅ **Complete** — fully independent and running.

### Structure

```
medical-service
├── config
│      SecurityConfig.java ✅
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
│
├── entity
│      MedicalProfile.java ✅
│      EmergencyContacts.java ✅
│      EmergencyAccessLog.java ✅
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
- Public Emergency Profile API
- **JWT validation only** — does not generate tokens, uses the same signing secret as Auth Service
- **No database access to Auth Service** — entities reference users by `userId` (Long) instead of a JPA relationship to `User`

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

### Key Architectural Change — Domain Model Redesign

**Before (Monolith):**
```java
@ManyToOne
@JoinColumn(name = "user_id")
private User user;
```

**After (Microservices):**
```java
@Column(nullable = false)
private Long userId;
```

Applied to `MedicalProfile`, `EmergencyContacts`, and `EmergencyAccessLog`. Medical Service no longer owns or can access the `User` entity — it only needs to know *which* user owns a record. Repository methods were updated to match (`findByUser(User)` → `findByUserId(Long)`).

### Key Architectural Change — JWT Authentication Without Database Lookup

**Before (Monolith):**
```
JWT → Extract Email → UserRepository → UserDetails → Authentication
```

**After (Medical Service):**
```
JWT → Validate Signature → Extract userId → SecurityContextHolder
```
```java
UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(
        userId,
        null,
        null
    );
```

Medical Service has no `UserRepository`, so it cannot look up users. Instead, it trusts the `userId` claim embedded directly in the JWT by Auth Service — eliminating a database call on every request.

---

## 🧠 Migration Principles Learned So Far

- **Migrating to microservices is not just moving Java classes.** Each service needs its own source code, dependencies, configuration, database, security setup, and build file (`pom.xml`).
- **Migrate bottom-up:** DTO → Entity → Repository → Service → Controller → Security → Exception. Controllers depend on Services, Services depend on Repositories, Repositories depend on Entities — migrating in this order makes compilation errors far easier to resolve.
- **Business logic can stay the same while ownership changes.** Most of the Java code in `auth-service` is unchanged from the monolith — what changed is that it now has its own database, its own dependencies, its own configuration, and can be deployed independently.
- **Cross-service JPA relationships are impossible.** Once a service no longer owns an entity, the relationship must be replaced with a plain identifier (`userId`) — not duplicated data, not cross-database joins.
- **Shared JWT secret enables decentralized authentication.** A single signing secret lets every service verify tokens independently — no token-introspection call back to Auth Service needed.
- **Encoding identity in the JWT avoids unnecessary database calls.** Adding `userId` and `role` as custom claims means downstream services can authenticate a request using only the token — no lookup required.

---

## ✅ Progress

- [x] Planned the microservices architecture
- [x] Designed clear service boundaries
- [x] Created two independent Spring Boot projects
- [x] Configured separate PostgreSQL databases (`auth_db`, `medical_db`)
- [x] Migrated the complete Authentication domain
- [x] Resolved dependency, configuration, and database migration issues
- [x] Successfully launched the first independent microservice — **auth-service**
- [x] Created the Medical microservice with its own independent database
- [x] Migrated the complete Medical domain (DTOs, Entities, Repositories, Services, Controllers)
- [x] Redesigned domain model — replaced JPA `User` relationships with `userId` references
- [x] Redesigned JWT to include custom claims (`userId`, `role`)
- [x] Implemented independent JWT validation in Medical Service (shared signing secret)
- [x] Redesigned Spring Security for Medical Service — `userId`-based authentication, no DB lookup
- [x] Successfully launched the second independent microservice — **medical-service**
- [ ] Service-to-service communication — Medical Service → Auth Service (`GET /api/users/public/{publicProfileId}`)
- [ ] API Gateway
- [ ] Eureka Service Registry
- [ ] Spring Cloud Config Server

---

## 📅 Current Status

**Both auth-service and medical-service are extracted and running independently**, each with their own database, security, and deployment.

One architectural gap remains: `EmergencyService` receives a `publicProfileId`, but Medical Service only stores `userId`. The fix is **not** to duplicate the `User` entity — instead, Medical Service will call Auth Service via REST:

```
Medical Service
        │
        ▼
GET /api/users/public/{publicProfileId}
        │
        ▼
Auth Service
        │
        ▼
UserPublicResponseDTO
        │
        ▼
Medical Service
        │
        ▼
Fetch Medical Profile using userId
```

Next milestone: **Service-to-Service Communication** — the first inter-service REST call in this architecture, laying the foundation for Eureka Service Discovery, API Gateway, and OpenFeign 🚀