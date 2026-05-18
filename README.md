# HandyFlow — Business Operating System

> South Africa's modular, multi-tenant business management platform.  
> One platform. Every industry. Total control.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [1. Clone the repo](#1-clone-the-repo)
  - [2. Start the database](#2-start-the-database)
  - [3. Run the backend](#3-run-the-backend)
  - [4. Run the frontend](#4-run-the-frontend)
- [Environment Variables](#environment-variables)
- [Database](#database)
- [Test Tenant](#test-tenant)
- [Module System](#module-system)
- [Project Structure](#project-structure)
- [API Reference](#api-reference)
- [Frontend Architecture](#frontend-architecture)
- [Adding a New Module](#adding-a-new-module)
- [Common Issues](#common-issues)
- [Backlog & Roadmap](#backlog--roadmap)

---

## Overview

HandyFlow is a multi-tenant SaaS platform built for South African SMEs. Companies register, choose their industry modules, and get a fully isolated workspace. All modules share a single backend, single database (row-level tenant isolation), and a single React frontend.

**Live modules:** CRM · Invoicing · Catalogue · Security · Fuel · Earthmoving · Property · Fleet · HR & Payroll · Accounting · Bookings · Events · Expenses · Contracting

**Core modules** (always free, every tenant): CRM · Invoicing · Catalogue

---

## Architecture

```
handyflow/
├── platform/          ← Spring Boot backend (Java 21)
└── handyflow-web/     ← React 19 frontend (Vite + TypeScript)
```

**Multi-tenancy:** Every DB query is automatically scoped to the tenant via `TenantContext`. There is no shared data between tenants — isolation is enforced at the repository layer.

**Modulith:** `platform` uses Spring Modulith. Each business domain (hr, property, fuel, etc.) is a self-contained module with enforced boundaries. No circular dependencies. Architecture tests run on every build.

**Billing:** Modules are activated per tenant with a 60-day free trial. The billing engine tracks trial state, accessible flag, and eventual recurring billing.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x, Spring Modulith |
| Database | PostgreSQL 16 |
| Migrations | Flyway (28 migrations, V1–V28) |
| Auth | JWT (access tokens, in-memory on frontend) |
| Frontend | React 19, TypeScript, Vite |
| Styling | Tailwind CSS v3 + inline styles |
| State | Zustand (auth), TanStack Query (server state) |
| Forms | React Hook Form + Zod |
| Container | Docker + Docker Compose |
| Build | Maven (backend), npm (frontend) |

---

## Prerequisites

Make sure you have these installed before starting:

| Tool | Version | Notes |
|---|---|---|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` — or use `./mvnw` |
| Node.js | 18+ | `node -version` |
| npm | 9+ | `npm -version` |
| Docker Desktop | Latest | Must be running |
| Git | Any | `git -version` |

> **Windows users:** Docker Desktop must be running before you start. All commands below work in PowerShell or Git Bash.

---

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/handyflow/platform.git
cd platform
```

Or if the frontend and backend are in separate repos:

```bash
# Backend
git clone https://github.com/handyflow/platform.git

# Frontend
git clone https://github.com/handyflow/handyflow-web.git
```

---

### 2. Start the database

PostgreSQL runs in Docker on port **5433** (not 5432 — avoids conflicts with any local Postgres).

```bash
cd platform
docker compose up -d
```

Verify it's running:

```bash
docker compose ps
# Should show handyflow-postgres as "Up"
```

The database is named `handyflow`, user `handyflow`, password `handyflow`.

Connect manually if needed:

```bash
# Windows (Docker Desktop path)
& "C:\Program Files\Docker\Docker\resources\bin\docker.exe" exec -it handyflow-postgres psql -U handyflow -d handyflow

# Mac/Linux
docker exec -it handyflow-postgres psql -U handyflow -d handyflow
```

---

### 3. Run the backend

```bash
cd platform
./mvnw spring-boot:run
```

**Windows PowerShell:**

```powershell
cd D:\Projects\HandyFlow\platform
.\mvnw.cmd spring-boot:run
```

The backend starts on **http://localhost:8080**.

Flyway migrations run automatically on startup — all 28 migrations (V1–V28) will be applied on first run. You'll see:

```
Flyway Community Edition ... will now auto-migrate database
Successfully applied 28 migration(s)
```

**Verify it's up:**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

**Swagger UI** (API docs):

```
http://localhost:8080/swagger-ui/index.html
```

---

### 4. Run the frontend

```bash
cd handyflow-web
npm install
npm run dev
```

Frontend starts on **http://localhost:5173**.

> The frontend proxies API requests to `http://localhost:8080`. No CORS configuration needed in development — see `vite.config.ts`.

---

## Environment Variables

### Backend (`platform/src/main/resources/application.yml`)

The default config works out of the box with Docker Compose. Override these for production:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/handyflow
    username: handyflow
    password: handyflow

handyflow:
  jwt:
    secret: your-256-bit-secret-key-here
    expiration-ms: 86400000   # 24 hours

  billing:
    default-trial-days: 60
    billing-anchor-day: 13    # Day of month billing runs
```

For local dev, copy `application.yml` to `application-local.yml` and run with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Frontend (`handyflow-web/.env`)

Create a `.env` file in `handyflow-web/`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

For production:

```env
VITE_API_BASE_URL=https://api.handyflow.co.za
```

---

## Database

### Docker Compose config

```yaml
# docker-compose.yml (in platform/)
services:
  postgres:
    image: postgres:16
    container_name: handyflow-postgres
    environment:
      POSTGRES_DB: handyflow
      POSTGRES_USER: handyflow
      POSTGRES_PASSWORD: handyflow
    ports:
      - "5433:5432"    # External 5433 → internal 5432
    volumes:
      - postgres_data:/var/lib/postgresql/data
```

### Migrations

Flyway manages all schema changes. Migration files live in:

```
platform/src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__tenant_modules.sql
  ...
  V28__latest.sql
```

**Never edit existing migrations.** Add a new file `V29__your_change.sql` for any schema changes.

### Reset the database (wipe all data)

```bash
docker compose down -v   # -v removes the volume
docker compose up -d
# Migrations re-run automatically on next backend start
```

---

## Test Tenant

A test tenant is pre-seeded with all 13 paid modules active. Use this for development and testing:

| Field | Value |
|---|---|
| Tenant slug | `zeta-earthmoving` |
| Email | `thabo@zeta-earthmoving.co.za` |
| Password | `Zeta@2024!` |
| Tenant ID | `9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f` |
| Billing anchor | Day 13 |
| Modules | All 13 paid modules on TRIAL |

**Login via API:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "thabo@zeta-earthmoving.co.za",
    "password": "Zeta@2024!",
    "tenantSlug": "zeta-earthmoving"
  }'
```

The response includes an `accessToken`. Pass it as `Authorization: Bearer <token>` on all subsequent requests.

> **Note:** JWT tokens are stored in memory (Zustand) — not localStorage. Refreshing the browser page logs you out. This is by design for security. Full refresh token support is on the roadmap.

---

## Module System

### How modules work

Every paid feature lives in a module. Modules are activated per tenant with a 60-day free trial.

**Core modules** (`crm`, `invoicing`, `catalogue`) are always active — they are never stored in `tenant_modules` and are not returned by `GET /api/v1/billing/modules/mine`. The frontend hardcodes these as always-accessible.

**Paid modules** are stored in `tenant_modules` with:

```
moduleKey   | e.g. "fuel", "security", "hr"
status      | TRIAL | ACTIVE | CANCELLED
accessible  | true/false
trialEndsAt | timestamp
```

### Backend module guard

Every controller that belongs to a module calls:

```java
featureGuard.requireModule("fuel");   // throws 403 if tenant doesn't have access
```

### Frontend module guard

The nav and dashboard tiles are built dynamically from `GET /api/v1/billing/modules/mine`. Core modules (crm, catalogue) are prepended in the frontend since they're not returned by the API.

---

## Project Structure

### Backend (`platform/`)

```
src/main/java/za/co/handyflow/platform/
├── auth/                   ← JWT login, registration, token generation
├── identity/               ← Users, tenants, UserController (stub)
├── billing/                ← Subscription, module catalogue, tenant modules
│   ├── api/
│   │   ├── ModuleController.java     ← /api/v1/billing/modules/*
│   │   └── SubscriptionController.java
│   └── dto/
│       ├── TenantModuleResponse.java
│       ├── CancelPreviewResponse.java
│       └── ModuleCatalogueResponse.java
├── crm/                    ← Customers, contacts
├── invoicing/              ← Quotes, invoices, line items, PDF generation
├── catalogue/              ← Products, services, categories
├── security/               ← Guards, sites, shifts, QR patrols
├── fuel/                   ← Tanks, suppliers, dispatch, deliveries
├── earthmoving/            ← Assets, operators, maintenance
├── property/               ← Properties, units, leases, payments, inspections
├── fleet/                  ← Vehicles, trips, service history
├── hr/                     ← Employees, leave, payroll, payslips
├── accounting/             ← Chart of accounts, journals, VAT
├── bookings/               ← Appointments, availability
├── events/                 ← Events, tickets, check-in
├── expenses/               ← Staff expense claims, approvals
├── contracting/            ← Contracts, OTP e-signing
└── shared/
    ├── ApiResponse.java    ← Standard response wrapper
    ├── TenantContext.java  ← Thread-local tenant ID
    └── TenantId.java       ← Value object
```

**`ApiResponse` pattern** — all endpoints return:

```json
{
  "success": true,
  "message": "Success",
  "data": { ... },
  "timestamp": "2026-05-18T..."
}
```

The frontend Axios interceptor unwraps this automatically — `res.data` is the payload, not the wrapper.

### Frontend (`handyflow-web/`)

```
src/
├── api/
│   ├── client.ts           ← Axios instance + ApiResponse unwrap interceptor
│   ├── auth.api.ts
│   └── crm.api.ts
├── components/
│   ├── layout/
│   │   └── ModuleLayout.tsx  ← Nav bar (dynamic), profile dropdown, notifications
│   └── ui/
│       ├── Button.tsx
│       ├── Input.tsx
│       ├── Card.tsx
│       ├── Badge.tsx
│       └── PageHeader.tsx
├── pages/
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   └── RegisterPage.tsx   ← 4-step: plan → company → modules → account
│   ├── dashboard/
│   │   └── DashboardPage.tsx  ← Dynamic tiles from billing API
│   ├── customers/             ← CRM
│   ├── quotes/
│   ├── invoices/
│   ├── catalogue/
│   ├── billing/               ← Module management (activate/cancel/preview)
│   ├── fuel/
│   ├── property/
│   ├── fleet/
│   ├── hr/
│   ├── security/
│   ├── earthmoving/
│   ├── accounting/
│   ├── bookings/
│   ├── events/
│   ├── expenses/
│   ├── contracts/
│   └── settings/
├── store/
│   └── auth.store.ts          ← Zustand: accessToken (in-memory), user
└── types/
    ├── auth.types.ts
    ├── billing.types.ts
    ├── crm.types.ts
    └── invoicing.types.ts
```

---

## API Reference

Full Swagger docs at `http://localhost:8080/swagger-ui/index.html`.

Key endpoints:

```
POST /api/v1/auth/login                          Login
POST /api/v1/auth/register                       Register new tenant

GET  /api/v1/billing/modules/mine                Tenant's active modules
POST /api/v1/billing/modules/activate            Activate a module
GET  /api/v1/billing/modules/{key}/cancel-preview  Cancel impact preview
DEL  /api/v1/billing/modules/{key}               Cancel a module

GET  /api/v1/crm/customers                       List customers (paginated)
POST /api/v1/crm/customers                       Create customer
PUT  /api/v1/crm/customers/{id}                  Update customer
DEL  /api/v1/crm/customers/{id}                  Delete customer

GET  /api/v1/invoicing/quotes                    List quotes
POST /api/v1/invoicing/quotes                    Create quote
POST /api/v1/invoicing/quotes/{id}/send          Send quote
POST /api/v1/invoicing/quotes/{id}/accept        Accept quote
POST /api/v1/invoicing/quotes/{id}/convert-to-invoice  Convert to invoice
GET  /api/v1/invoicing/quotes/{id}/pdf           Download quote PDF

GET  /api/v1/invoicing/invoices                  List invoices
POST /api/v1/invoicing/invoices/{id}/issue       Issue draft invoice
POST /api/v1/invoicing/invoices/{id}/payments    Mark invoice paid

GET  /api/v1/property/properties                 List properties (units NOT included)
GET  /api/v1/property/properties/{id}            Single property WITH units
GET  /api/v1/property/units?propertyId=&status=  List units (use this for units)
POST /api/v1/property/leases/{id}/terminate      Terminate a lease

GET  /api/v1/hr/employees                        List employees
GET  /api/v1/fuel/suppliers                      List fuel suppliers (flat array)
PUT  /api/v1/fuel/suppliers/{id}                 Update supplier
```

> **Important:** `GET /api/v1/property/properties` returns `units: []` always (list endpoint doesn't load units for performance). Use `GET /api/v1/property/units?propertyId={id}` to get units for a property.

---

## Frontend Architecture

### Auth flow

1. User logs in → backend returns `accessToken`
2. Token stored in Zustand (`auth.store.ts`) — **in memory only, not localStorage**
3. Axios interceptor attaches `Authorization: Bearer <token>` to every request
4. On 401, user is redirected to `/login`
5. Page refresh = logged out (by design — refresh token support is roadmap item)

### API response unwrapping

The Axios interceptor in `client.ts` unwraps `ApiResponse` automatically:

```typescript
// What the API returns:
{ success: true, message: "Success", data: { content: [...] } }

// What res.data is in your component:
{ content: [...] }

// So for paginated responses:
const items = res.data.content

// For single objects:
const item = res.data
```

### Design tokens

```typescript
// Brand colors (used throughout — no CSS variables)
const NAVY  = '#1B3A6B'   // Primary buttons, headers
const TEAL  = '#0D9488'   // Active states, accents, CTA
const DARK  = '#0F172A'   // Body text
const MUTED = '#64748B'   // Secondary text
const LIGHT = '#F8FAFC'   // Page background
const BORDER= '#E2E8F0'   // Card borders

// Tab active state pattern
borderBottom: isActive ? '2px solid #0D9488' : '2px solid transparent'
```

---

## Adding a New Module

### Backend

1. Create the module directory: `src/main/java/za/co/handyflow/platform/yourmodule/`
2. Add domain model, repository, service, controller following the existing pattern
3. Add the module guard in every controller: `featureGuard.requireModule("yourmodule");`
4. Create a Flyway migration `V29__yourmodule_tables.sql`
5. Register the module key in the billing seed data

### Frontend

1. Create page directory: `src/pages/yourmodule/YourModulePage.tsx`
2. Add route in `App.tsx`:
   ```tsx
   <Route path="/yourmodule" element={<YourModulePage />} />
   ```
3. Add to `MODULE_REGISTRY` in both `DashboardPage.tsx` and `ModuleLayout.tsx`:
   ```typescript
   yourmodule: { icon: YourIcon, label: 'Your Module', route: '/yourmodule' }
   ```
4. Add icon + color to `MODULE_COLORS` in `DashboardPage.tsx`

The module will automatically appear in the nav and dashboard tiles once the tenant activates it via the billing API.

---

## Common Issues

### `lower(bytea) does not exist` — HR employees 500 error

**Cause:** Hibernate passes a null search param as `bytea` to PostgreSQL.

**Fix:** In `HrEmployeeRepository.java`, use `CAST(:search AS string)` instead of `:search` in the JPQL query.

---

### Frontend shows 13 modules but CRM and Catalogue are missing

**Cause:** `GET /api/v1/billing/modules/mine` never returns `crm` or `catalogue` — they're core/free and not stored in `tenant_modules`.

**Fix:** Both `ModuleLayout.tsx` and `DashboardPage.tsx` hardcode `CORE_ALWAYS = ['crm', 'catalogue']` and prepend them before the API results. If you see this issue, make sure you're using the latest version of both files.

---

### Property units always empty

**Cause:** `GET /api/v1/property/properties` (list) does not populate `units[]` — this is by design for performance. Only `GET /api/v1/property/properties/{id}` includes units.

**Fix:** Fetch units directly from `GET /api/v1/property/units?size=200` and group by `propertyId` in the frontend.

---

### Docker port conflict

If port 5433 is already in use:

```bash
# Find what's using it
netstat -ano | findstr :5433       # Windows
lsof -i :5433                      # Mac/Linux

# Or change the port in docker-compose.yml
ports:
  - "5434:5432"   # Use 5434 instead
# Then update application.yml accordingly
```

---

### Backend won't start — migration error

If Flyway fails on startup, the database state may be inconsistent. To reset:

```bash
docker compose down -v
docker compose up -d
# Wait 5 seconds for Postgres to be ready
./mvnw spring-boot:run
```

---

### JWT token expired / 401 on all requests

Tokens are in-memory — they're lost on page refresh or after 24 hours. Simply log in again. Full refresh token support is on the roadmap.

---

## Backlog & Roadmap

See the full living backlog in the project wiki. Current priorities:

**In progress / Q3 2026:**
- Multi-user per tenant with role & permissions system
- Update profile + change password (backend `UserController` stub exists, needs implementation)
- Forgot password flow
- Onboarding enforcement (guided setup checklist)
- Real-time notifications
- Accounts payable module

**Q4 2026:**
- HandyFlow Admin Portal (superadmin — tenant management, incident inbox, revenue analytics)
- Mobile app (React Native) — Fuel dispatch, Security patrols, Expense capture
- Desktop app (Tauri) — POS & Stock with offline + barcode support
- Marketing, Surveys, Recruiter, Desk Support, Assets, Tasks, Creative, POS & Stock modules

---

## Contributing

1. Branch from `main`: `git checkout -b feature/your-feature`
2. Backend changes must pass architecture tests: `./mvnw test`
3. Never edit existing Flyway migrations — add a new `V{n+1}__description.sql`
4. Follow the `ApiResponse.success("message", data)` pattern — message first, data second
5. All new controllers must call `featureGuard.requireModule("key")` if they belong to a paid module
6. Frontend: add new module to `MODULE_REGISTRY` in both `ModuleLayout.tsx` and `DashboardPage.tsx`

---

## License

Proprietary — HandyFlow (Pty) Ltd. All rights reserved.

---

*Built with ❤️ for South African businesses.*
