# Cortex — Task Manager API

![Java](https://img.shields.io/badge/Java-17-ED8B00?labelColor=555&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?labelColor=555&logo=spring)
![Gradle](https://img.shields.io/badge/Gradle-9.5-02303A?labelColor=555&logo=gradle)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?labelColor=555&logo=postgresql)
![License](https://img.shields.io/badge/license-MIT-blue?labelColor=555)

A hands-on learning project — building a Task Manager REST API from scratch and deploying it to Kubernetes with a full CI/CD pipeline.

**The goal:** experience the entire path from Java code to production-grade deployment, understanding how each technology connects to the next.

---

## 📋 Project Status

| # | Phase | Status | Description |
|---|-------|--------|-------------|
| 0 | Environment Setup | ✅ | Java 17, Docker, Git, Gradle wrapper |
| 1 | Project Generation | ✅ | Spring Boot 4.1.0 with core dependencies |
| 2 | Swagger / OpenAPI | ✅ | springdoc 2.8.6 + Lombok added |
| 3 | MVC Structure | ✅ | model, repository, service, controller, dto, exception |
| 4 | Configuration | ✅ | application.yml + Liquibase migrations |
| 5 | Local Run | ✅ | All CRUD endpoints verified, Swagger UI, Liquibase migration |
| 6 | Tests | ✅ | 20/20 pass (Service: 9, Controller: 10, Context: 1) |
| 7 | Dockerfile | ✅ | Multi-stage build, 168MB image, verified |
| 8 | Docker Compose | ✅ | app + PostgreSQL, single command, verified |
| 9 | Kubernetes | ✅ | Minikube, ConfigMap, Secret, PostgreSQL + app deployments, 2 replicas, liveness/readiness probes |
| 10 | CI/CD | ✅ | GitHub Actions: test → build & push to ghcr.io → deploy to kind + smoke test |
| 11 | Oracle Cloud Instance | ⏳ | Provision ARM VM, open ports |
| 12 | k3s Setup | ⏳ | Install k3s on the instance |
| 13 | Production Manifests | ⏳ | Ingress, cert-manager, TLS |
| 14 | Deploy-to-Oracle CI/CD | ⏳ | Auto-deploy on git push via kubectl |
| 15 | Domain & HTTPS | ⏳ | Domain + Let's Encrypt |

---

## 🚀 Quick Start

### Prerequisites

```bash
java -version    # 17+
docker --version
git --version
```

### Build & run

```bash
# Compile
./gradlew compileJava

# Run tests
./gradlew test

# Start with Docker Compose (app + PostgreSQL)
docker compose up --build

# Start development server (requires PostgreSQL on localhost:5432)
./gradlew bootRun
```

---

## 🧩 API Endpoints

| Method | Path | Action | Status |
|--------|------|--------|--------|
| POST | `/api/tasks` | Create a task | ✅ |
| GET | `/api/tasks` | List all tasks | ✅ |
| GET | `/api/tasks/{id}` | Get task by ID | ✅ |
| GET | `/api/tasks?status=` | Filter by status | ✅ |
| PUT | `/api/tasks/{id}` | Update a task | ✅ |
| DELETE | `/api/tasks/{id}` | Delete a task | ✅ |

### Utility endpoints

| Path | Description | Status |
|------|-------------|--------|
| `/swagger-ui.html` | Swagger UI | ✅ |
| `/actuator/health/liveness` | Kubernetes liveness probe | ✅ |
| `/actuator/health/readiness` | Kubernetes readiness probe | ✅ |

---

## 📁 Project Structure

```
cortex/
├── .github/workflows/       # CI/CD pipelines
├── docker-compose.yml       # Local dev with PostgreSQL
├── Dockerfile               # Multi-stage build
├── k8s/                     # Kubernetes manifests
├── src/
│   ├── main/
│   │   ├── java/com/ortex/cortex/
│   │   │   ├── controller/     # REST controllers
│   │   │   ├── dto/            # Request/Response DTOs
│   │   │   ├── exception/      # Error handling
│   │   │   ├── model/          # JPA entities
│   │   │   ├── repository/     # Data access
│   │   │   ├── service/        # Business logic
│   │   │   └── CortexApplication.java
│   │   ├── resources/
│   │   │   └── application.yml
│   │   └── ...
│   └── test/
└── README.md
```

---

## 🛠 Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 17 (Temurin) |
| Framework | Spring Boot 4.1.0 |
| Build | Gradle (via wrapper) |
| Database | PostgreSQL 16 |
| Migrations | Liquibase |
| API Docs | Swagger / OpenAPI (springdoc) |
| Container | Docker (multi-stage build) |
| Orchestration | Kubernetes / Minikube |
| CI/CD | GitHub Actions → ghcr.io → kind |

## 🧪 Testing

| Layer | Framework | Tests |
|-------|-----------|-------|
| Service | JUnit 5 + Mockito | 9 (all CRUD + edge cases) |
| Controller | JUnit 5 + Mockito + MockMvc standalone | 10 (all endpoints + validation + 404) |
| Context | Spring Boot Test | 1 (application context loads)

---

## 📄 Full Plan

See [`docs/task-manager-plan.md`](docs/task-manager-plan.md) for the complete step-by-step guide covering all 10 phases.

---

## 📝 License

This project is created for educational purposes.
