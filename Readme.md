# 🏥 MedInfo

MedInfo is a Spring Boot application that allows users to store their medical information and generate a QR code for emergency access. In a medical emergency, first responders can scan the QR code to instantly access critical health information — no login required.

**Personal Story:** This project was built after the developer underwent ENT surgery due to hypertension and realized that in an emergency, no one would have access to their medical history. MedInfo solves this problem.

---

## 🚀 Features Implemented

### 👤 User Registration
- Register with full name, email, and password
- Email uniqueness validation
- Password encryption using BCrypt
- UUID generated automatically as public profile ID
- Request validation using `@NotBlank`, `@Email`, `@Size`

### 🔐 User Login
- Login using email and password
- Secure password verification using BCrypt
- Returns signed JWT token on successful login

### 🛡️ JWT Authentication
- Stateless authentication — no sessions stored on server
- JWT generated on login using JJWT library
- Token contains: subject (email), issued-at, expiration (15 minutes)
- Every protected request validated via JWT filter
- `JWTService` — generates, validates, and extracts claims from tokens
- `CustomUserDetailsService` — loads user from DB using email extracted from JWT
- `JWTAuthenticationFilter` — runs on every request, validates token, sets Spring Security context

### 🩺 Medical Profile Management
- One User ↔ One Medical Profile (`@OneToOne`)
- Full CRUD — Create, Read, Update, Delete
- JWT-protected — only logged-in user can access their own profile
- Current user identified via `SecurityContextHolder`
- Stores: age, gender, blood group, height, weight, allergies, medical conditions, medications, organ donor status

### 🆘 Emergency Contacts Management
- One User ↔ Many Emergency Contacts (`@ManyToOne`)
- Full CRUD — Create, Read, Update, Delete
- JWT-protected — only logged-in user can manage their contacts
- Ownership validation — users can only modify their own contacts
- Stores: name, relationship, phone number
- `@PathVariable` used to identify specific contact for update/delete

### 🚨 Public Emergency Access
- `GET /api/emergency/{uuid}` — no login required
- UUID generated at registration — impossible to guess (not sequential IDs)
- Returns full medical profile + emergency contacts in a single response
- Prevents IDOR — Insecure Direct Object Reference attack
- Foundation for QR code scanning in emergencies

### 🧾 Emergency Access Audit Logging
- Every successful access to `GET /api/emergency/{publicProfileId}` is recorded
- Dedicated `EmergencyAccessLog` entity — preserves complete access history (not just last access)
- Captures: access timestamp (`@CreationTimestamp`), IP address, user agent, access method
- `AccessMethod` enum (`QR`, `URL`) — type-safe, easily extensible (future: `MOBILE_APP`, `NFC`)
- `EmergencyAccessLogService` — separated from `EmergencyService` to follow Single Responsibility Principle
- Foundation for future Notifications, Analytics, Kafka event publishing, and Security Auditing
- Controller remains unchanged — only `EmergencyService` delegates to the new logging service

### ⚠️ Global Exception Handling
- Centralized exception management with `@RestControllerAdvice`
- Clean JSON error responses for all exceptions
- Validation error handling (`MethodArgumentNotValidException`)

### 💾 Database
- PostgreSQL via Neon (cloud-hosted, free tier)
- Spring Data JPA + Hibernate ORM
- Data persists across restarts — no more data loss on restart
- Custom repository methods (`findByEmail`, `existsByEmail`, `findByUser`, `existsByUser`, `findAllByUser`, `findByPublicProfileId`, `findAllByUser` for audit logs)

### 🔒 Security
- Spring Security with stateless session policy
- BCrypt password hashing
- JWT-protected APIs (`/api/auth/**` and `/api/emergency/**` public, everything else requires token)
- Ownership validation on all record-specific operations
- UUID-based public access — prevents sequential ID enumeration
- CSRF disabled (REST API — no browser sessions)

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Security | Spring Security + JWT (JJWT) |
| Database | PostgreSQL via Neon (cloud) |
| ORM | Spring Data JPA + Hibernate |
| Build | Maven |
| Utilities | Lombok, DevTools |

---

## 📂 Project Structure

```
com.MedInfo
├── config          # SecurityFilterChain, PasswordEncoder bean
├── controller      # AuthController, MedicalProfileController,
│                   # EmergencyContactsController, EmergencyController
├── dto             # RegisterRequestDTO, LoginRequestDTO,
│                   # CreateMedicalProfileDTO, MedicalProfileResponseDTO,
│                   # CreateEContactDTO, EContactsDTO,
│                   # EmergencyProfileResponseDTO
├── entity          # User, MedicalProfile, EmergencyContacts, EmergencyAccessLog
├── enums           # AccessMethod
├── exception       # GlobalExceptionHandler
├── repository      # UserRepository, MedicalProfileRepository,
│                   # EmergencyContactsRepository, EmergencyAccessLogRepository
├── security        # JWTService, JWTAuthenticationFilter,
│                   # CustomUserDetailsService
├── service         # AuthService, MedicalProfileService,
│                   # EmergencyContactsService, EmergencyService,
│                   # EmergencyAccessLogService
└── util            # ApiResponse wrapper
```

---

## 📌 APIs

### Auth APIs (Public)

**Register User**
```
POST /api/auth/register
```
Request:
```json
{
  "fullName": "Narendra Kumar",
  "email": "narendra@gmail.com",
  "password": "password123"
}
```
Response:
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": null
}
```

**Login User**
```
POST /api/auth/login
```
Request:
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

### Medical Profile APIs (JWT Protected)

All requests require: `Authorization: Bearer <jwt_token>`

**Create Medical Profile**
```
POST /api/profile
```
Request:
```json
{
  "age": 24,
  "gender": "Male",
  "bloodGroup": "O+",
  "height": 175.0,
  "weight": 70.0,
  "allergies": "Dust Allergy",
  "medicalConditions": "Hypertension",
  "currentMedications": "Telma H, Met XL",
  "organDonor": true
}
```
Response:
```json
{
  "success": true,
  "message": "Medical Profile Created Successfully",
  "data": null
}
```

**Get Medical Profile**
```
GET /api/profile
```
Response:
```json
{
  "success": true,
  "message": "Profile fetched",
  "data": {
    "age": 24,
    "gender": "Male",
    "bloodGroup": "O+",
    "height": 175.0,
    "weight": 70.0,
    "allergies": "Dust Allergy",
    "medicalConditions": "Hypertension",
    "currentMedications": "Telma H, Met XL",
    "organDonor": true
  }
}
```

**Update Medical Profile**
```
PUT /api/profile
```
Request: Same structure as Create — send updated fields.

Response:
```json
{
  "success": true,
  "message": "Profile Updated Successfully",
  "data": null
}
```

**Delete Medical Profile**
```
DELETE /api/profile
```
Response:
```json
{
  "success": true,
  "message": "Profile Deleted Successfully",
  "data": null
}
```

### Emergency Contacts APIs (JWT Protected)

All requests require: `Authorization: Bearer <jwt_token>`

**Create Emergency Contact**
```
POST /api/contacts
```
Request:
```json
{
  "name": "Venkateshwarlu",
  "relationship": "Father",
  "phoneNumber": "9999999999"
}
```
Response:
```json
{
  "success": true,
  "message": "Emergency Contact Added Successfully",
  "data": null
}
```

**Get All Emergency Contacts**
```
GET /api/contacts
```
Response:
```json
{
  "success": true,
  "message": "Contacts fetched",
  "data": [
    {
      "id": 1,
      "name": "Venkateshwarlu",
      "relationship": "Father",
      "phoneNumber": "9999999999"
    },
    {
      "id": 2,
      "name": "Lakshmi",
      "relationship": "Mother",
      "phoneNumber": "8888888888"
    }
  ]
}
```

**Update Emergency Contact**
```
PUT /api/contacts/{id}
```
Example: `PUT /api/contacts/1`

Request:
```json
{
  "name": "Venkateshwarlu",
  "relationship": "Father",
  "phoneNumber": "7777777777"
}
```
Response:
```json
{
  "success": true,
  "message": "Contact Updated Successfully",
  "data": null
}
```

**Delete Emergency Contact**
```
DELETE /api/contacts/{id}
```
Example: `DELETE /api/contacts/1`

Response:
```json
{
  "success": true,
  "message": "Contact Deleted Successfully",
  "data": null
}
```

### Public Emergency API (No Login Required)

**Get Emergency Profile by UUID**
```
GET /api/emergency/{publicProfileId}
```
Example: `GET /api/emergency/550e8400-e29b-41d4-a716-446655440000`

No Authorization header needed. Designed for emergency responders. **Every successful call to this endpoint is now recorded in the audit log** (IP address, user agent, timestamp, access method).

Response:
```json
{
  "fullName": "Narendra Kumar",
  "age": 24,
  "gender": "Male",
  "bloodGroup": "O+",
  "allergies": "Dust Allergy",
  "medicalConditions": "Hypertension",
  "currentMedications": "Telma H, Met XL",
  "organDonor": true,
  "emergencyContacts": [
    {
      "id": 1,
      "name": "Venkateshwarlu",
      "relationship": "Father",
      "phoneNumber": "9999999999"
    }
  ]
}
```

---

## 🔑 JWT Flow

```
POST /api/auth/login
        ↓
Find user by email → BCrypt verify password
        ↓
Generate JWT (email + iat + exp embedded)
        ↓
Return token to client

--- On every subsequent request ---

Authorization: Bearer <token>
        ↓
JWTAuthenticationFilter intercepts
        ↓
Extract token → Extract email → Load user from DB
        ↓
Validate token (email match + not expired)
        ↓
Store in SecurityContextHolder → Request proceeds
        ↓
Service calls SecurityContextHolder.getContext()
        .getAuthentication().getName() → gets current user email
```

---

## 🆘 Emergency Access Flow (with Audit Logging)

```
User Registers
        ↓
UUID generated automatically (publicProfileId)
        ↓
User creates Medical Profile + Emergency Contacts
        ↓
Public URL: GET /api/emergency/{uuid}
        ↓
Future: QR Code generated from this URL
        ↓
Emergency Responder scans QR
        ↓
EmergencyService finds user → logs access (EmergencyAccessLogService)
        ↓
Medical data returned instantly — no login needed
```

---

## 🏗️ Data Architecture

```
User
 │
 ├── publicProfileId (UUID — for public emergency access)
 │
 ├── MedicalProfile    (1:1)  @OneToOne
 │    └── age, gender, blood group, height, weight,
 │        allergies, conditions, medications, organDonor
 │
 ├── EmergencyContacts (1:N)  @ManyToOne
 │    └── name, relationship, phoneNumber
 │
 └── EmergencyAccessLog (1:N)  @ManyToOne
      └── accessTime, ipAddress, userAgent, accessMethod
```

---

## 🔒 Security Architecture

```
/api/auth/**        → Public (register, login)
/api/emergency/**   → Public (UUID-based, no login) — now audited
Everything else     → JWT Required
```

---

## ⚙️ Configuration

`application.properties`:
```properties
# PostgreSQL — Neon
spring.datasource.url=jdbc:postgresql://<neon-host>/neondb?sslmode=require
spring.datasource.username=<username>
spring.datasource.password=<password>
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# JWT
jwt.secret=<your-secret-key>
jwt.expiration=900000

# Server
server.port=8080
```
⚠️ Never commit real credentials to Git. Use environment variables in production.

---

## ✅ Current State

- ✅ Spring Boot Backend
- ✅ JWT Authentication
- ✅ PostgreSQL
- ✅ CRUD APIs
- ✅ Security
- ✅ Global Exception Handling
- ✅ Public Emergency Access (UUID-based)
- ✅ Emergency Access Audit Logging

## ✅ Completed Milestones

- [x] Project Setup
- [x] PostgreSQL via Neon (cloud database)
- [x] Spring Security Setup
- [x] User Entity with publicProfileId (UUID)
- [x] User Repository
- [x] Registration API — UUID auto-generated
- [x] Login API
- [x] Password Encryption (BCrypt)
- [x] DTO Validation (`@NotBlank`, `@Email`, `@Size`, `@Positive`, `@Pattern`)
- [x] Global Exception Handling
- [x] JWT Dependencies (JJWT)
- [x] JWTService — generate, validate, extract claims
- [x] CustomUserDetailsService
- [x] JWTAuthenticationFilter (OncePerRequestFilter)
- [x] SecurityFilterChain — stateless, JWT-protected routes
- [x] Protected APIs working end-to-end
- [x] MedicalProfile Entity (`@OneToOne`) with age and gender
- [x] MedicalProfile Repository (findByUser, existsByUser)
- [x] Medical Profile DTOs (Request + Response)
- [x] Create Medical Profile API — POST /api/profile
- [x] Get Medical Profile API — GET /api/profile
- [x] Update Medical Profile API — PUT /api/profile
- [x] Delete Medical Profile API — DELETE /api/profile
- [x] SecurityContextHolder for current user identification
- [x] EmergencyContacts Entity (`@ManyToOne` with User)
- [x] EmergencyContacts Repository (findAllByUser, existsByUser)
- [x] Emergency Contact DTOs (Request + Response)
- [x] Create Emergency Contact API — POST /api/contacts
- [x] Get All Contacts API — GET /api/contacts
- [x] Update Contact API — PUT /api/contacts/{id}
- [x] Delete Contact API — DELETE /api/contacts/{id}
- [x] Ownership validation on update and delete
- [x] List Entity to List DTO mapping (stream + map + toList)
- [x] Public Emergency endpoint — GET /api/emergency/{uuid}
- [x] EmergencyProfileResponseDTO
- [x] SecurityConfig updated — emergency route public
- [x] IDOR prevention via UUID
- [x] EmergencyAccessLog entity (`@ManyToOne` with User)
- [x] AccessMethod enum (QR, URL)
- [x] EmergencyAccessLogRepository (findAllByUser)
- [x] EmergencyAccessLogService — IP, user-agent, timestamp capture
- [x] EmergencyService updated to log access before returning profile
- [x] Full CRUD tested in Postman

---

## 🔜 Roadmap

### Architecture
- [ ] Convert Monolith → Microservices
- [ ] API Gateway
- [ ] Eureka Service Registry
- [ ] Spring Cloud Config Server

### Event Driven Architecture
- [ ] Kafka Producer
- [ ] Kafka Consumer
- [ ] Topics
- [ ] Partitions
- [ ] Consumer Groups
- [ ] Retry
- [ ] Dead Letter Queue
- [ ] Idempotency

**Use Cases:**
- User Registration Notification
- Emergency Access Notification

### Caching
- [ ] Redis
- [ ] `@Cacheable`
- [ ] `@CacheEvict`
- [ ] `@CachePut`
- [ ] TTL
- [ ] Cache Aside Pattern

### DevOps
- [ ] Docker
- [ ] Docker Compose
- [ ] CI/CD Pipeline
- [ ] GitHub Actions / Jenkins
- [ ] Environment Variables
- [ ] Profiles

### API Quality
- [ ] Swagger / OpenAPI
- [ ] Logging (SLF4J)
- [ ] Actuator
- [ ] Monitoring Basics
- [ ] Unit Testing (JUnit + Mockito)

### Frontend (Low Priority)
Simple React App. Only:
- Login
- Dashboard
- Medical Profile
- Emergency Contacts
- QR Code

Nothing fancy.

---

## 🧠 Learning Objectives

This project is being built to strengthen practical knowledge of:

- Spring Boot + Auto Configuration
- Spring Security + JWT (Stateless)
- REST API Development
- JPA & Hibernate (`@OneToOne`, `@ManyToOne`, custom queries)
- DTO Pattern — Request and Response separation
- Builder Pattern (Lombok `@Builder`)
- Repository Pattern (Spring Data JPA)
- SecurityContextHolder
- Ownership Validation (IDOR prevention)
- UUID for public access security
- `@PathVariable`, `@RequestBody`, `@Valid`
- List Entity to List DTO mapping
- Cloud PostgreSQL (Neon)
- Audit Logging & Single Responsibility Principle
- Clean Layered Architecture
- (Upcoming) Microservices, Kafka, Redis, Docker, CI/CD

---

## 📅 Current Status

**Phase 4 Complete** — Emergency Access Audit Logging module done.

```
Registration → Login → JWT → Medical Profile CRUD
→ Emergency Contacts CRUD → Public Emergency Access
→ Emergency Access Audit Logging ✅
```

Next milestone: **Microservices Architecture (API Gateway + Eureka + Config Server)** 🚀