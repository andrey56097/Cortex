# Project Plan: Task Manager API
**Stack:** Java 17 + Spring Boot 4.1.0 + Gradle + PostgreSQL 16 + Liquibase + Swagger/OpenAPI + Docker + Kubernetes + GitHub Actions CI/CD

The goal is to walk the entire path from code to deployment in Kubernetes through a CI/CD pipeline, understanding how each technology connects to the next.

---

## Phase 0. Environment Setup

Check installed versions:

```bash
java -version        # needs 17+
docker --version
docker compose version
git --version
```

Tools for later phases (can install now or as needed):
- **Gradle** — no separate install needed, the project includes a Gradle wrapper (`gradlew`)
- **Minikube** — https://minikube.sigs.k8s.io/docs/start/
- **kubectl** — https://kubernetes.io/docs/tasks/tools/
- **GitHub account** — for CI/CD and ghcr.io (Container Registry)

> **kind** is not needed locally — it's created automatically in CI via `helm/kind-action`.

---

## Phase 1. Project Generation

Go to **https://start.spring.io** and select:

| Field | Value |
|---|---|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | Latest stable 4.x |
| Group | `com.ortex` |
| Artifact | `cortex` |
| Java | 17 |

**Dependencies (add via search):**
- `Spring Web`
- `Spring Data JPA`
- `PostgreSQL Driver`
- `Validation`
- `Spring Boot Actuator` (provides `/actuator/health` — needed for K8s liveness/readiness probes)

Download the zip, extract it, open in IDE (IntelliJ IDEA Community works great).

> Swagger is not available in Initializr — we add it manually in `build.gradle` in the next phase.

---

## Phase 2. Dependencies in build.gradle

Manually add to the `dependencies` block:

```groovy
// Swagger / OpenAPI
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'

// Lombok (optional, but saves a lot of boilerplate)
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'

// Liquibase (database schema migrations instead of ddl-auto)
implementation 'org.liquibase:liquibase-core:4.30.0'
```

After saving — Gradle resolves dependencies (IDE does this automatically, or run `./gradlew build`).

> **Note:** Spring Boot 4.x removed auto-config for Liquibase. Create a `LiquibaseConfig.java` with `@Bean` returning `SpringLiquibase`.

---

## Phase 3. Project Structure (MVC)

Create packages inside `src/main/java/com/ortex/cortex/`:

```
cortex/
├── CortexApplication.java
├── model/
│   ├── Task.java          // @Entity
│   └── TaskStatus.java    // enum: TODO, IN_PROGRESS, DONE
├── repository/
│   └── TaskRepository.java   // extends JpaRepository<Task, Long>
├── service/
│   └── TaskService.java       // business logic, CRUD
├── controller/
│   └── TaskController.java    // REST endpoints
├── dto/
│   ├── TaskRequest.java       // what we accept from the client
│   └── TaskResponse.java      // what we return to the client
├── exception/
│   ├── TaskNotFoundException.java
│   └── GlobalExceptionHandler.java   // @ControllerAdvice
├── config/
│   └── LiquibaseConfig.java          // manual Liquibase @Configuration
```

### What each layer should contain

**model/Task.java** — JPA Entity:
- fields: `id`, `title`, `description`, `status` (enum), `createdAt`, `updatedAt`
- annotations: `@Entity`, `@Table(name = "tasks")`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `@PrePersist` / `@PreUpdate` for auto-timestamps

**repository/TaskRepository.java**:
```java
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatus(TaskStatus status);
}
```
Spring Data JPA generates the implementation automatically — that's the magic of Spring Data.

**service/TaskService.java** — methods:
- `createTask(TaskRequest)`
- `getAllTasks(String status)` — optional filter by status
- `getTaskById(Long id)` → throws `TaskNotFoundException` if missing
- `updateTask(Long id, TaskRequest)`
- `deleteTask(Long id)`

**controller/TaskController.java** — REST endpoints:

| Method | Path | Action |
|---|---|---|
| POST | `/api/tasks` | Create a task |
| GET | `/api/tasks` | List all tasks |
| GET | `/api/tasks/{id}` | Get one task |
| PUT | `/api/tasks/{id}` | Update a task |
| DELETE | `/api/tasks/{id}` | Delete a task |
| GET | `/api/tasks?status=DONE` | Filter by status (optional) |

Use `@RestController`, `@RequestMapping("/api/tasks")`, `@Valid` on DTO in `@RequestBody`.

**exception/GlobalExceptionHandler.java** — `@ControllerAdvice` catching `TaskNotFoundException` → returns 404, and `MethodArgumentNotValidException` → 400 with validation error descriptions.

---

## Phase 4. Configuration (application.yml)

Create `src/main/resources/application.yml` (delete `application.properties`):

```yaml
spring:
  application:
    name: Cortex

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:cortex}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}

  jpa:
    hibernate:
      ddl-auto: none    # migrations via Liquibase
    show-sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true   # enables /actuator/health/liveness and /readiness

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

> **Important:** values use environment variables (`${DB_HOST:localhost}`) — this will be needed later in Docker and Kubernetes where the database is on a different host.

---

## Phase 5. Local Run & Verification

Start PostgreSQL locally via Docker (easier than installing the database directly):

```bash
docker run --name pg-cortex -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=cortex -p 5432:5432 -d postgres:16
```

Run the application:
```bash
./gradlew bootRun
```

Verify:
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Create a task via Swagger UI or curl:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Learn Docker","description":"Multi-stage build","status":"TODO"}'
```

> **Note:** The first app launch runs Liquibase migration `001-create-tasks-table.xml` which creates the `tasks` table automatically.

✅ **Checkpoint 1:** API works locally, CRUD functions, Swagger opens.

---

## Phase 6. Tests

Minimum for this learning project:
- `TaskServiceTest` — unit test with Mockito (`@ExtendWith(MockitoExtension.class)`)
- `TaskControllerTest` — `MockMvcBuilders.standaloneSetup()` (Spring Boot 4.x removed `@WebMvcTest` from auto-configuration, and `@MockBean`/`@MockitoBean` are no longer available)
- Context load test — `@SpringBootTest` verifies the application context starts

```bash
./gradlew test
```

✅ **Checkpoint 2:** `./gradlew test` is green (20 tests: 9 service + 10 controller + 1 context).

---

## Phase 7. Dockerfile (multi-stage build)

Create `Dockerfile` in the project root:

```dockerfile
# ---- Stage 1: Build ----
FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /app/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage:** the first stage needs the full Gradle + JDK (heavy image), but the final image only needs JRE + the built JAR. The result is much smaller and has no build tools (reduced attack surface).

**Note on Apple Silicon:** Alpine variants (`-alpine`) don't have ARM64 images. Use `gradle:8.10.2-jdk17` and `eclipse-temurin:17-jre` (non-alpine) instead.

Build and check:
```bash
docker build -t cortex:1.0 .
docker images | grep cortex   # check image size (~168MB)
```

---

## Phase 8. Docker Compose (app + database together)

Create `docker-compose.yml` in the root:

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: cortex
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: cortex
      DB_USER: postgres
      DB_PASSWORD: postgres
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
```

Run:
```bash
docker compose up --build
```

✅ **Checkpoint 3:** App and database start with a single command, Swagger is accessible on `:8080`.

---

## Phase 9. Kubernetes — Local Cluster

### 9.1 Tool choice
- **Minikube** — recommended for first exposure, has dashboard and addons
- **kind** — lighter and faster, used later in CI/CD

Start Minikube (already installed):
```bash
minikube start --driver=docker --memory=4096 --cpus=2
minikube addons enable ingress
minikube addons enable metrics-server
kubectl get nodes      # verify cluster is alive
```

### 9.2 Manifests — create `k8s/` folder in the project

**k8s/configmap.yaml** — non-sensitive settings:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cortex-config
data:
  DB_HOST: "postgres-service"
  DB_PORT: "5432"
  DB_NAME: "cortex"
```

**k8s/secret.yaml** — password (in production managed via `kubectl create secret`, but YAML is fine for learning):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: cortex-secret
type: Opaque
stringData:
  DB_USER: postgres
  DB_PASSWORD: postgres
```

**k8s/postgres-deployment.yaml** — database as Deployment + Service:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16
          env:
            - name: POSTGRES_DB
              valueFrom:
                configMapKeyRef: { name: cortex-config, key: DB_NAME }
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef: { name: cortex-secret, key: DB_USER }
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: cortex-secret, key: DB_PASSWORD }
          ports:
            - containerPort: 5432
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
```

**k8s/app-deployment.yaml** — your application:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cortex
spec:
  replicas: 2
  selector:
    matchLabels:
      app: cortex
  template:
    metadata:
      labels:
        app: cortex
    spec:
      containers:
        - name: cortex
          image: cortex:1.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef: { name: cortex-config }
            - secretRef: { name: cortex-secret }
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
            initialDelaySeconds: 15
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: cortex-service
spec:
  type: NodePort
  selector:
    app: cortex
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

### 9.3 Build image inside Minikube

Important trick — so Minikube can see your locally built image without a registry:

```bash
eval $(minikube docker-env)      # switch Docker CLI to Minikube's daemon
docker build -t cortex:1.0 .
```

### 9.4 Deploy

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/app-deployment.yaml

kubectl get pods -w        # watch pods transition to Running
kubectl get svc

minikube service cortex-service --url   # get the URL to test
```

✅ **Checkpoint 4:** Application runs in Kubernetes, accessible via NodePort, pods are stable (liveness/readiness work).

Useful debugging commands:
```bash
kubectl logs <pod-name>
kubectl describe pod <pod-name>
kubectl exec -it <pod-name> -- sh
kubectl scale deployment cortex --replicas=3
```

---

## Phase 10. CI/CD — GitHub Actions

### 10.1 Preparation
1. Create a repository on GitHub, push the project.
2. Registry — use **GitHub Container Registry (ghcr.io)**: free, `GITHUB_TOKEN` is auto-generated in Actions, no separate registration needed.

### 10.2 Pipeline — create `.github/workflows/ci-cd.yaml`

```yaml
name: CI/CD

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

env:
  IMAGE_NAME: ghcr.io/${{ github.repository }}

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        run: ./gradlew test

  build-and-push:
    needs: test
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:latest
            ${{ env.IMAGE_NAME }}:${{ github.sha }}

  deploy-to-kind:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Create kind cluster
        uses: helm/kind-action@v1
        with:
          cluster_name: ci-cluster
      - name: Load image into kind
        run: |
          docker pull ${{ env.IMAGE_NAME }}:${{ github.sha }}
          kind load docker-image ${{ env.IMAGE_NAME }}:${{ github.sha }} --name ci-cluster
      - name: Deploy PostgreSQL
        run: |
          kubectl apply -f k8s/configmap.yaml
          kubectl apply -f k8s/secret.yaml
          kubectl apply -f k8s/postgres-deployment.yaml
          kubectl wait --for=condition=ready pod -l app=postgres --timeout=120s
      - name: Deploy application
        run: |
          sed "s|image: cortex:1.0|image: ${{ env.IMAGE_NAME }}:${{ github.sha }}|" k8s/app-deployment.yaml | kubectl apply -f -
          kubectl wait --for=condition=ready pod -l app=cortex --timeout=120s
      - name: Smoke test
        run: |
          kubectl port-forward svc/cortex-service 8080:8080 &
          sleep 5
          curl -f http://localhost:8080/actuator/health
          curl -f -X POST http://localhost:8080/api/tasks \
            -H "Content-Type: application/json" \
            -d '{"title":"CI test","description":"Created in pipeline","status":"TODO"}'
```

What this pipeline does step by step:
1. **test** — runs unit tests on every push/PR
2. **build-and-push** — only on `main`, after successful tests: builds Docker image and pushes to `ghcr.io`
3. **deploy-to-kind** — creates a temporary kind cluster in CI, deploys the app, and runs a smoke test (checks `/actuator/health`) — this simulates a deploy without a real production cluster

✅ **Checkpoint 5 (final):** push to `main` → tests → build image → publish to ghcr.io → deploy to ephemeral cluster → health check. The full DevOps cycle is complete.

---

## Knowledge

- Docker: multi-stage build, reduced image size, docker-compose for local development
- Kubernetes: Deployment, Service, ConfigMap, Secret, liveness/readiness probes, scaling
- CI/CD: GitHub Actions, automated tests, Docker image build and publish to container registry, automated deploy to cluster

---

## Troubleshooting

- `kubectl describe pod <name>` and `kubectl logs <name>` — the first things to check on CrashLoopBackOff
- If the pod can't find the image — check `imagePullPolicy: IfNotPresent` and that the image was built in Minikube's Docker daemon (`eval $(minikube docker-env)`)
- If the database won't connect — check that the `postgres-service` resolves (`kubectl exec -it <app-pod> -- nslookup postgres-service`)
- If tests fail — make sure you're not using removed APIs (`@MockBean`, `@MockitoBean`, `@WebMvcTest` from old packages — they're gone in Spring Boot 4.x)
- If Liquibase fails to find changelog — use `db/changelog/db.changelog-master.xml` without `classpath:` prefix
- Come back to me at any stage — we'll figure out the specific error/log together
