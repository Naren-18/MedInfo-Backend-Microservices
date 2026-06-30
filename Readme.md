# 🏥 MedInfo — Microservices

MedInfo is being migrated from a Spring Boot monolith into independent microservices. This repository is a **monorepo** containing all services that make up the MedInfo backend.

In a medical emergency, first responders can scan a QR code to instantly access critical health information — no login required. This migration restructures the original monolith into independently deployable services while keeping that core mission intact.

---

## 📂 Repository Structure

```
MedInfo-Backend-Microservices
├── auth-service        # Authentication & user identity
├── medical-service      # Medical profiles, contacts, emergency access (in progress)
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
- JWT generation (subject, issued-at, expiration)
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

---

## 🩺 medical-service

Status: 🚧 **In Progress**

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

Will own: Medical Profile, Emergency Contacts, Emergency Access Log, Public Emergency Profile APIs.

**Next major step:** removing JPA relationships to the `User` entity and replacing them with service-friendly references (`userId`) — required since each service now owns its own database and can no longer share entity relationships across service boundaries.

---

## 🧠 Migration Principles Learned So Far

- **Migrating to microservices is not just moving Java classes.** Each service needs its own source code, dependencies, configuration, database, security setup, and build file (`pom.xml`).
- **Migrate bottom-up:** Entity → Repository → DTO → Service → Controller → Security → Exception. Controllers depend on Services, Services depend on Repositories, Repositories depend on Entities — migrating in this order makes compilation errors far easier to resolve.
- **Business logic can stay the same while ownership changes.** Most of the Java code in `auth-service` is unchanged from the monolith — what changed is that it now has its own database, its own dependencies, its own configuration, and can be deployed independently.

---

## ✅ Progress

- [x] Planned the microservices architecture
- [x] Designed clear service boundaries
- [x] Created two independent Spring Boot projects
- [x] Configured separate PostgreSQL databases (`auth_db`, `medical_db`)
- [x] Migrated the complete Authentication domain
- [x] Resolved dependency, configuration, and database migration issues
- [x] Successfully launched the first independent microservice — **auth-service**
- [ ] Migrate Medical Service (Medical Profile, Emergency Contacts, Emergency Access Log)
- [ ] Replace JPA `@ManyToOne`/`@OneToOne` User relationships with `userId` references in Medical Service
- [ ] API Gateway
- [ ] Eureka Service Registry
- [ ] Spring Cloud Config Server

---

## 📅 Current Status

**auth-service extracted and running independently.**

Next milestone: **Migrate Medical Service** — including the first major domain refactor (removing direct JPA relationships to `User`, replacing with `userId` references) 🚀