# CLAUDE.md — Cortex Project

## Project Overview

Cortex is a learning project — a Task Manager REST API deployed to Kubernetes via CI/CD. The full plan is at `docs/task-manager-plan.md`.

**All 10 phases are complete.** The project covers the full DevOps cycle: code → test → Docker → Docker Compose → Kubernetes → CI/CD.

**Tech stack:** Java 17 + Spring Boot 4.1.0 + Gradle + PostgreSQL 16 + Liquibase + Swagger/OpenAPI + Docker + Kubernetes/Minikube + GitHub Actions CI/CD

## Workflow Rules

### After making changes
- **Run tests** before committing: `./gradlew test`
- **Update `README.md`** if project status, API endpoints, or configuration changes
- **Update `CLAUDE.md`** if workflows or conventions change

### Build & run commands
```bash
./gradlew compileJava        # verify compilation
./gradlew test               # run all tests (20 tests)

# Local dev (requires PostgreSQL)
./gradlew bootRun

# Docker Compose (app + PostgreSQL in one network)
docker compose up --build

# Kubernetes (Minikube)
eval $(minikube docker-env)
docker build -t cortex:1.0 .
kubectl apply -f k8s/
minikube service cortex-service --url
```

### Project structure
```
cortex/
├── .github/workflows/ci-cd.yaml   # CI/CD pipeline
├── docker-compose.yml              # App + PostgreSQL
├── Dockerfile                      # Multi-stage build
├── k8s/                            # Kubernetes manifests
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── postgres-deployment.yaml
│   └── app-deployment.yaml
├── src/main/resources/
│   ├── application.yml
│   └── db/changelog/               # Liquibase migrations
└── src/main/java/com/ortex/cortex/
    ├── controller/                 # REST controllers
    ├── dto/                        # Request/Response DTOs
    ├── exception/                  # Error handling
    ├── model/                      # JPA entities
    ├── repository/                 # Data access
    ├── service/                    # Business logic
    └── config/                     # Java config (Liquibase)
```

### Key notes
- Spring Boot 4.1.0 **removed** `@MockBean`/`@MockitoBean` and Liquibase auto-config — use `MockMvcBuilders.standaloneSetup()` and manual `@Configuration` for Liquibase
- Alpine Docker images don't have ARM64 variants for Apple Silicon — use `gradle:8.10.2-jdk17` and `eclipse-temurin:17-jre`
- kind is not needed locally — it's created automatically in CI via `helm/kind-action`
