# CLAUDE.md — Cortex Project

## Project Overview

Cortex is a learning project — building a Task Manager REST API and deploying it to Kubernetes via CI/CD. The full plan is at `docs/task-manager-plan.md`.

**Tech stack:** Java 17 + Spring Boot 4.1.0 + Gradle + PostgreSQL 16 + Swagger/OpenAPI + Docker + Kubernetes/Local + GitHub Actions

## Workflow Rules

### After completing each phase
- **Update `README.md`** — change the corresponding phase status from ⏳ to ✅ and add a brief summary of what was done under that phase
- **Commit with a descriptive message** referencing the phase number (e.g., `Phase 2: add Swagger & Lombok dependencies`)
- If the phase introduced new API endpoints or config, add them to the README tables

### Build & test commands
```bash
./gradlew compileJava    # verify compilation
./gradlew test           # run tests
./gradlew bootRun        # start dev server (needs PostgreSQL)
```

### Phase numbering
Phases are numbered 0–10 in the plan. Always reference them by number (e.g., "Phase 3", "Phase 9.2").
