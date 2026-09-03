# MultiVault

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Multi-tenant document management system with physical data isolation, multi-issuer JWT authentication, and S3-compatible object storage.

---

## Overview

MultiVault is a SaaS-ready document management backend that provides **complete data isolation** between tenants using PostgreSQL schema-per-tenant architecture. Each tenant operates within its own database schema, authenticates through its own OIDC Identity Provider, and stores documents in isolated object storage paths.

The system is built with a strong emphasis on security (WORM audit logs, API key hashing, multi-issuer JWT validation), scalability (Redis caching, streaming uploads), and clean architecture (event-driven auditing, MapStruct DTO mapping, well-defined transactional boundaries).

## Architecture

```
                          ┌──────────────────────┐
                          │      HTTP Client     │
                          └──────────┬───────────┘
                                     │
                          ┌──────────▼───────────┐
                          │    REST API (MVC)    │
                          │  /api/v1/tenants     │
                          │  /api/v1/documents   │
                          └──────────┬───────────┘
                                     │
               ┌─────────────────────┼─────────────────────┐
               │                     │                     │
    ┌──────────▼──────────┐  ┌───────▼────────┐  ┌─────────▼────────┐
    │  ApiKeyAuthFilter   │  │ JwtAuthFilter  │  │ TenantCtxFilter  │
    │  (X-API-Key header) │  │ (Bearer token) │  │ (search_path)    │
    └──────────┬──────────┘  └───────┬────────┘  └─────────┬────────┘
               │                     │                     │
               └─────────────────────┼─────────────────────┘
                                     │
                          ┌──────────▼───────────┐
                          │     Service Layer    │
                          │ TenantService        │
                          │ DocumentService      │
                          │ ApiKeyService        │
                          │ AuditEventPublisher  │
                          └──────────┬───────────┘
                                     │
               ┌─────────────────────┼──────────────────────┐
               │                     │                      │
    ┌──────────▼──────────┐ ┌───────▼────────┐  ┌─────────▼─────────┐
    │   PostgreSQL 16     │ │   Redis 7      │  │  Backblaze B2     │
    │ ┌────────────────┐  │ │  (Cache)       │  │  (S3-compatible)  │
    │ │  public schema │  │ │  - apiKeys     │  │  - Document       │
    │ │  (shared)      │  │ │  - jwks        │  │    content        │
    │ ├────────────────┤  │ │                │  │                   │
    │ │ tenant schema  │  │ └────────────────┘  └───────────────────┘
    │ │ (per-tenant)   │  │
    │ └────────────────┘  │
    └─────────────────────┘
```

## Key Features

- **Schema-per-tenant isolation** — each tenant gets its own PostgreSQL schema with independent Flyway migrations
- **Multi-issuer JWT authentication** — per-tenant OIDC Identity Providers with JWKS caching and key rotation support
- **Dual API key system** — SERVICE keys (machine-to-machine) and STANDARD keys (requires a JWT from the same tenant). The initial onboarding key is a SERVICE master key that grants all scopes (`*`)
- **Cross-tenant protection** — the tenant-settings endpoints (`identity-provider`, `status`) are service-to-service, restrict to SERVICE keys, and derive the tenant from the authenticated principal instead of the URL path, closing the IDOR vector
- **Document versioning** — immutable version history with server-side SHA-256 checksums
- **WORM audit trail** — write-once-read-many audit log with event-driven persistence (AFTER_COMMIT)
- **S3-compatible storage** — Backblaze B2 integration with streaming uploads and constant memory usage
- **Tenant lifecycle management** — cancel, suspend, and reinstate with cascading effects on subscriptions, keys, and members
- **Redis caching** — API key hashes (5 min TTL) and JWKS keys (10 min TTL) with Jackson 3 serialization
- **Plan-based quotas** — FREE, PRO, BUSINESS, ENTERPRISE tiers with storage, user, and rate limits. Storage quota is enforced at upload time: exceeding the plan's `max_storage_bytes` returns HTTP 409 (ADR-0013)
- **Soft deletes** — documents and folders use logical deletion with `deleted_at` timestamps

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 16 |
| Cache | Redis 7 (via Spring Cache + Lettuce) |
| Object Storage | Backblaze B2 (S3-compatible) |
| Migrations | Flyway (public schema + programmatic per-tenant) |
| Authentication | Spring Security, JJWT 0.13.0, API keys |
| DTO Mapping | MapStruct 1.6.3 |
| Utilities | Lombok |
| Build | Maven 3.9+ (Maven Wrapper included) |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |
| AWS SDK | AWS SDK for S3 v2.31.25 |

## Project Structure

```
src/main/java/dev/achiri/multivault/
├── MultivaultApplication.java
│
├── apikey/                          # API key management
│   ├── model/                       # ApiKey entity, ApiKeyType enum
│   ├── repository/                  # ApiKeyRepository
│   └── service/                     # ApiKeyService, ApiKeyHasher, ApiKeyUsageRecorder
│
├── audit/                           # WORM audit trail
│   ├── model/                       # AuditLog entity, ActorType enum
│   ├── repository/                  # AuditLogRepository
│   └── event/                       # AuditEvent, publisher, listener, context
│
├── common/                          # Cross-cutting concerns
│   ├── exception/                   # GlobalExceptionHandler + custom exceptions
│   ├── response/                    # ErrorResponse DTO
│   └── util/                        # SlugUtils
│
├── document/                        # Document management (per-tenant scope)
│   ├── model/                       # Document, DocumentVersion entities
│   ├── repository/                  # DocumentRepository, DocumentVersionRepository
│   ├── controller/                  # DocumentController
│   ├── service/                     # DocumentService, UploadPolicy, DocumentHashUtil
│   ├── dto/                         # Request/Response records
│   ├── mapper/                      # DocumentMapper (MapStruct)
│   └── config/                      # UploadConfig, UploadProperties
│
├── plan/                            # Subscription plans
│   ├── model/                       # Plan entity, PlanCode enum
│   └── repository/                  # PlanRepository
│
├── subscription/                    # Tenant subscriptions
│   ├── model/                       # Subscription entity, SubscriptionStatus enum
│   ├── repository/                  # SubscriptionRepository
│   └── mapper/                      # SubscriptionMapper
│
├── tenant/                          # Tenant management
│   ├── model/                       # Tenant, TenantIdentityProvider, TenantMember, TenantUsage
│   ├── repository/                  # TenantRepository, TenantMemberRepository, etc.
│   ├── controller/                  # TenantController
│   ├── service/                     # TenantService, TenantLifecycleService, TenantMemberService
│   ├── provisioning/                # Schema provisioning + activation
│   ├── dto/                         # Request/Response records
│   └── mapper/                      # TenantMapper, TenantMemberMapper, etc.
│
└── infrastructure/                  # Framework configuration
    ├── async/                       # AsyncConfig
    ├── cache/                       # RedisCacheConfig
    ├── web/                         # WebMvcConfig, AuditContextArgumentResolver
    ├── persistence/                 # JPA base entities, auditing, multi-tenant routing
    │   ├── base/                    # BaseEntity, DateAudit, SoftDeletable
    │   ├── auditing/                # AuditorAwareImpl
    │   ├── config/                  # JpaConfig, TenantSchemaFilterProvider
    │   └── tenant/                  # TenantContext, TenantContextFilter, schema provisioner
    │       ├── context/             # ThreadLocal holder + filter
    │       └── hibernate/           # Multi-tenant connection provider + identifier resolver
    ├── security/                    # Authentication infrastructure
    │   ├── config/                  # SecurityConfig, JwtProperties
    │   ├── apikey/                  # ApiKeyAuthenticationFilter, ApiKeyAuthenticator
    │   ├── jwt/                     # JwtAuthenticationFilter, MultiIssuerJwtDecoder, JwksProvider
    │   ├── codec/                   # JJWT <-> Jackson 3 serializers
    │   └── handler/                 # RestAuthenticationEntryPoint
    └── storage/                     # Object storage abstraction
        ├── DocumentStorageService   # Interface
        └── backblaze/               # BackblazeB2 implementation (S3Client, S3Presigner)
```

## Prerequisites

- **Java 21** or later
- **Docker** (required for Testcontainers during tests, and for PostgreSQL/Redis in development)
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)

## Getting Started

1. **Clone the repository**

   ```bash
   git clone https://github.com/achiridev/multivault.git
   cd multivault
   ```

2. **Start infrastructure** (PostgreSQL + Redis)

   ```bash
   docker run -d --name multivault-pg -e POSTGRES_DB=multivault -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine
   docker run -d --name multivault-redis -p 6379:6379 redis:7-alpine
   ```

3. **Configure environment variables** (or use `application-local.yml`)

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/multivault
   export DB_USERNAME=postgres
   export DB_PASSWORD=postgres
   export JWT_SECRET=your-256-bit-secret-key-here
   ```

4. **Run the application**

   ```bash
   ./mvnw spring-boot:run
   ```

   The API starts on `http://localhost:8080`.

5. **Run tests**

   ```bash
   ./mvnw test
   ```

   Tests use Testcontainers to spin up real PostgreSQL 16 and Redis 7 instances. Docker must be running.

## Configuration

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/multivault` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | — | HMAC secret for JWT signing (required) |
| `JWT_EXPIRATION` | `3600000` | JWT expiration in milliseconds (default: 1 hour) |
| `B2_ENABLED` | `false` | Enable Backblaze B2 storage |
| `B2_ENDPOINT` | — | B2 S3-compatible endpoint URL |
| `B2_REGION` | — | B2 region |
| `B2_ACCESS_KEY` | — | B2 application key ID |
| `B2_SECRET_KEY` | — | B2 application key |
| `B2_BUCKET` | — | B2 bucket name |
| `UPLOAD_MAX_SIZE_BYTES` | `104857600` | Max upload size (default: 100 MB) |
| `UPLOAD_ALLOWED_MIME_TYPES` | — | Comma-separated allowlist (empty = allow all) |

### Profiles

| Profile | Description |
|---|---|
| `local` | Development with local PostgreSQL, Redis, and B2 credentials |
| `test` | Integration tests with Testcontainers (B2 disabled) |

## API Endpoints

All endpoints are prefixed with `/api/v1`. Only `POST /tenants` is public; all others require authentication.

### Tenant Management

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/tenants` | Create tenant (onboarding) | Public |
| `PUT` | `/tenants/identity-provider` | Update OIDC config | API Key SERVICE |
| `PUT` | `/tenants/status` | Cancel, suspend, or reinstate | API Key SERVICE |

### Documents (per-tenant scope)

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/documents` | Upload document with first version | API Key / JWT |
| `POST` | `/documents/{documentId}/versions` | Upload new immutable version | API Key / JWT |
| `GET` | `/documents/{documentId}` | Get document with current version | API Key / JWT |

Document upload responses:

| Status | Meaning |
|---|---|
| `201` | Created (document/version) |
| `400` | Empty/missing file, invalid body, missing `ownerUserId` with SERVICE key |
| `404` | Document not found or soft-deleted (versions endpoint) |
| `409` | Plan storage quota exceeded (`tenant_usage.storage_bytes_used` vs `plan.max_storage_bytes`) |
| `413` | File exceeds `UPLOAD_MAX_SIZE_BYTES` (per-file limit) |
| `415` | MIME type not in `UPLOAD_ALLOWED_MIME_TYPES` allowlist |

### Authentication

API keys are passed via `Authorization: Bearer mv_live_...` or `X-API-Key` header.

- **SERVICE keys** authenticate alone (machine-to-machine)
- **STANDARD keys** require a valid JWT from the same tenant

### Create Tenant Example

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Inc",
    "planId": "<plan-uuid>",
    "admin": {
      "subject": "user_123",
      "email": "admin@acme.com",
      "displayName": "Jane Doe"
    },
    "identityProvider": {
      "issuer": "https://idp.acme.com",
      "jwksUri": "https://idp.acme.com/.well-known/jwks.json",
      "audience": "https://api.acme.com",
      "allowedAlgorithms": ["RS256"]
    }
  }'
```

The response includes the initial SERVICE master key (shown only once), which has the wildcard scope `*` and can manage the tenant's own configuration.

## Multi-Tenancy

MultiVault uses **schema-per-tenant** isolation: each tenant gets its own PostgreSQL schema with independent tables, triggers, and Flyway migration history.

```
PostgreSQL
├── public (shared)
│   ├── plan
│   ├── tenant
│   ├── subscription
│   ├── tenant_identity_provider
│   ├── api_key
│   ├── tenant_member
│   ├── tenant_usage
│   └── audit_log
│
├── mv_acme_inc (tenant A)
│   ├── folder
│   ├── document
│   ├── document_version
│   └── document_permission
│
└── mv_globex (tenant B)
    ├── folder
    ├── document
    ├── document_version
    └── document_permission
```

**How routing works:** On each request, `TenantContextFilter` extracts the tenant from the authenticated principal, resolves the schema name, and sets `SET search_path TO "<schema>"` on the JDBC connection. Queries automatically route to the correct tenant's data.

**Schema provisioning:** When a tenant is created (`POST /tenants`), the system creates the schema, runs Flyway migrations from `db/tenant/`, and activates the tenant in a multi-stage process with rollback on failure.

## Testing

The project includes **28 test files** covering unit and integration tests:

- **Unit tests** — Pure logic with Mockito (entities, mappers, hashers, validators)
- **Integration tests** — Full HTTP-layer testing with `MockMvc` against real PostgreSQL 16 and Redis 7 via Testcontainers

### Test Coverage Areas

| Domain | Tests |
|---|---|
| Tenant provisioning & lifecycle | Schema creation, activation, cancel/suspend/reinstate, isolation |
| Document flow | Upload, versioning, validation, checksums, storage keys, rollback |
| Authentication | JWT multi-issuer, API key validation, filter chain, JWKS caching |
| Audit | Event publishing, AFTER_COMMIT persistence, actor resolution |
| Infrastructure | Redis connectivity, B2 storage service |

### Running Tests

```bash
./mvnw test
```

Requires Docker running (Testcontainers spins up PostgreSQL and Redis containers).

## Documentation

Full project documentation is available in the [`docs/`](docs/INDEX.md) directory:

- [Architecture & Stack](docs/01-Arquitectura/Arquitectura.md)
- [Multi-Tenancy Strategy](docs/01-Arquitectura/MultiTenant.md)
- [Authentication Model](docs/01-Arquitectura/Autenticacion.md)
- [Storage Strategy](docs/01-Arquitectura/Storage.md)
- [Redis & Caching](docs/01-Arquitectura/Redis.md)
- [Security Considerations](docs/01-Arquitectura/Seguridad.md)
- [API Reference](docs/02-Backend/API.md)
- [Database Schema](docs/02-Backend/BaseDatos.md)
- [Architecture Decision Records](docs/06-Decisiones/) — 13 ADRs documenting key technical decisions

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.
