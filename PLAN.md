# Lab5 Project – Updated Implementation Plan

## Overview
The Lab5 demo implements a restaurant order workflow composed of four independent gRPC services (Order, Kitchen, Accounting, Orchestrator).  The new requirements add real databases, saga‑based transaction management, Docker deployment, CI/CD, and a simple front‑end UI.

---
### Phase 1 – Persistence & Data Modeling
- **Database choice**: PostgreSQL (can be switched to MySQL by changing the JDBC URL). One dedicated DB per service.
- **Schemas**:
  - **order_service**: `orders(id UUID PK, status VARCHAR, created_at TIMESTAMP, updated_at TIMESTAMP)`
  - **kitchen_service**: `tickets(id UUID PK, order_id UUID FK, status VARCHAR, created_at TIMESTAMP)`
  - **accounting_service**: `transactions(id UUID PK, order_id UUID FK, amount NUMERIC(10,2), status VARCHAR, created_at TIMESTAMP)`
- **Tech**: Spring Data JPA with Hibernate.  Each service will have its own `spring.datasource.*` properties (URL, username, password) and a `JpaRepository` for the entity.
- **Implementation steps**:
  1. Add `spring-boot-starter-data-jpa` and the PostgreSQL driver to each module’s `pom.xml`.
  2. Create entity classes (`OrderEntity`, `TicketEntity`, `TransactionEntity`).
  3. Create repository interfaces (`OrderRepository`, …).
  4. Replace the current in‑memory maps in the service implementations with calls to the repositories (save, findById, update).
  5. Add Flyway migration scripts under `src/main/resources/db/migration` for each service to create the tables.

---
### Phase 2 – Saga Orchestration Logic (Orchestrator Service)
- **Orchestrator flow** (Happy Path):
  1. Call `OrderService.CreateOrder` → receives `orderId`.
  2. Call `KitchenService.CreateTicket(orderId)`.
  3. Call `AccountingService.AuthorizePayment(orderId, amount)`.
  4. If all succeed, call `OrderService.UpdateStatus(orderId, APPROVED)`.
- **Compensation (Rollback) Path** (triggered when Accounting returns error, e.g., amount > $100):
  1. Call `KitchenService.CancelTicket(orderId)` (or RejectTicket).
  2. Call `OrderService.UpdateStatus(orderId, REJECTED)`.
- **Implementation notes**:
  * Orchestrator will use **gRPC blocking stubs** and wrap each call in a try/catch.
  * Define a custom `SagaException` for failures.
  * Keep the orchestrator stateless – all state lives in the databases.
  * Add logging for each step, including compensation actions.

---
### Phase 3 – Dockerization (Deployment Layer)
- **Dockerfile** (one per service) – multistage build:
  1. `FROM maven:3.9‑eclipse-temurin-21 AS build` – run `mvn clean package -DskipTests`.
  2. `FROM eclipse-temurin:21‑jre` – copy the shaded jar from the build stage and set `ENTRYPOINT` to run the service.
- **docker‑compose.yml** will define 7 containers:
  * `order-service`, `kitchen-service`, `accounting-service`, `orchestrator-service`
  * `order-db`, `kitchen-db`, `accounting-db` (official `postgres:16` images)
  * Networks: a common `backend` network; each service connects to its own DB via network alias (`order-db`, …).
- **Environment variables** (in compose) for DB connection strings, usernames, passwords.
- **Healthchecks** – simple `curl` to the gRPC health service (or a tiny HTTP endpoint).

---
### Phase 4 – CI/CD Pipeline (GitHub Actions)
Create `.github/workflows/main.yml` with the following jobs:
1. **build** – runs on `ubuntu‑latest`:
   * Checkout repo.
   * Set up JDK 21.
   * `mvn clean package`.
2. **docker** – depends on *build*:
   * Log in to Docker Hub (secrets `DOCKER_USER`, `DOCKER_PASS`).
   * Build each Docker image with tags `yourdockerhub/lab5‑order:latest` etc.
   * Push images.
3. **test‑saga** – optional integration test using `docker‑compose up -d` and a script that invokes the orchestrator with a low‑amount and a high‑amount, asserting the final order status.

---
### Phase 5 – Front‑End & Validation
- **UI**: a tiny React app (or plain HTML/JS) served on port 3000.
  * Form fields: `orderId` (auto‑generated UUID) and `amount`.
  * Submit button calls a small **gateway** endpoint (we add a simple Spring‑Boot HTTP controller in the Orchestrator service exposing `POST /order`). The controller forwards the request to the saga orchestrator.
  * Show the final status returned (`APPROVED` or `REJECTED`).
- **End‑to‑End Tests** (Jest/Cypress or Selenium):
  1. Deploy stack with `docker‑compose up`.
  2. Run test that submits amount 50 → expects `APPROVED` and verifies a row exists in the `orders` table with that status.
  3. Run test that submits amount 150 → expects `REJECTED`, verifies the ticket was removed/cancelled and the transaction was not persisted.

---
## Milestones & Timeline (≈ 30 h total)
| Milestone | Approx. effort |
|-----------|----------------|
| Phase 1 – DB & JPA | 6 h |
| Phase 2 – Saga orchestrator | 5 h |
| Phase 3 – Dockerfiles & compose | 4 h |
| Phase 4 – GitHub Actions CI/CD | 5 h |
| Phase 5 – Front‑end + e2e tests | 6 h |
| Polish, documentation, README | 4 h |
| **Total** | **30 h** |

---
### Verification Checklist
- `mvn clean verify` passes locally.
- `docker compose up --build` brings up all 7 containers without errors.
- UI can create an order with amount < 100 → order row status **APPROVED**.
- UI with amount > 100 → order status **REJECTED**, ticket record removed, no transaction persisted.
- GitHub Actions workflow runs end‑to‑end on push.

---
> **User Review Required**
> Please confirm the chosen database (PostgreSQL vs MySQL), any naming conventions, and whether the UI should be React or plain HTML/JS. Once approved we can begin implementing Phase 1.
