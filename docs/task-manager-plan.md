# План проекта: Task Manager API
**Стек:** Java 17 + Spring Boot 3.5.x + Gradle + PostgreSQL + Swagger/OpenAPI + Docker + Kubernetes + GitHub Actions CI/CD

Цель — пройти весь путь от кода до деплоя в Kubernetes через CI/CD пайплайн, чтобы на практике понять, как эти технологии связаны друг с другом.

---

## Этап 0. Подготовка окружения

Установи и проверь версии:

```bash
java -version        # нужен 17+
docker --version
docker compose version
git --version
```

Инструменты для следующих этапов (можно поставить сейчас или по ходу):
- **Gradle** — можно не ставить отдельно, проект сгенерирует gradle wrapper (`gradlew`)
- **Minikube** — https://minikube.sigs.k8s.io/docs/start/
- **kubectl** — https://kubernetes.io/docs/tasks/tools/
- **GitHub аккаунт** — для CI/CD и ghcr.io (Container Registry)

> **kind** не нужен локально — он автоматически создается в CI через `helm/kind-action`.

---

## Этап 1. Генерация проекта

Иди на **https://start.spring.io** и выбери:

| Поле | Значение |
|---|---|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 3.5.x (последняя стабильная из линии 3.x) |
| Group | `com.example` |
| Artifact | `task-manager` |
| Java | 17 |

**Dependencies (добавь через поиск):**
- `Spring Web`
- `Spring Data JPA`
- `PostgreSQL Driver`
- `Validation`
- `Spring Boot Actuator` (даёт `/actuator/health` — понадобится для K8s liveness/readiness проб)

Скачай zip, разархивируй, открой в IDE (IntelliJ IDEA Community подходит отлично).

> Swagger в Initializr нет — добавим его вручную в `build.gradle` на следующем этапе.

---

## Этап 2. Зависимости в build.gradle

Добавь вручную в блок `dependencies`:

```groovy
// Swagger / OpenAPI
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

// Lombok (опционально, но экономит много кода)
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

После сохранения — Gradle подтянет зависимости (в IDE обычно автоматически, либо `./gradlew build`).

---

## Этап 3. Структура проекта (MVC)

Создай пакеты внутри `src/main/java/com/example/taskmanager/`:

```
taskmanager/
├── TaskManagerApplication.java
├── model/
│   ├── Task.java          // @Entity
│   └── TaskStatus.java    // enum: TODO, IN_PROGRESS, DONE
├── repository/
│   └── TaskRepository.java   // extends JpaRepository<Task, Long>
├── service/
│   └── TaskService.java       // бизнес-логика, CRUD
├── controller/
│   └── TaskController.java    // REST эндпоинты
├── dto/
│   ├── TaskRequest.java       // что принимаем от клиента
│   └── TaskResponse.java      // что отдаём клиенту
└── exception/
    ├── TaskNotFoundException.java
    └── GlobalExceptionHandler.java   // @ControllerAdvice
```

### Что должно быть в каждом слое

**model/Task.java** — JPA Entity:
- поля: `id`, `title`, `description`, `status` (enum), `createdAt`, `updatedAt`
- аннотации: `@Entity`, `@Table(name = "tasks")`, `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `@PrePersist` / `@PreUpdate` для авто-заполнения дат

**repository/TaskRepository.java**:
```java
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatus(TaskStatus status);
}
```
Spring Data JPA сам сгенерирует реализацию — это вся магия Spring Data.

**service/TaskService.java** — методы:
- `createTask(TaskRequest)`
- `getAllTasks()`
- `getTaskById(Long id)` → бросает `TaskNotFoundException`, если нет
- `updateTask(Long id, TaskRequest)`
- `deleteTask(Long id)`

**controller/TaskController.java** — REST эндпоинты:

| Метод | Путь | Действие |
|---|---|---|
| POST | `/api/tasks` | создать задачу |
| GET | `/api/tasks` | список всех задач |
| GET | `/api/tasks/{id}` | получить одну задачу |
| PUT | `/api/tasks/{id}` | обновить задачу |
| DELETE | `/api/tasks/{id}` | удалить задачу |
| GET | `/api/tasks?status=DONE` | фильтр по статусу (опционально) |

Используй `@RestController`, `@RequestMapping("/api/tasks")`, `@Valid` на DTO в `@RequestBody`.

**exception/GlobalExceptionHandler.java** — `@ControllerAdvice`, который ловит `TaskNotFoundException` → возвращает 404, и `MethodArgumentNotValidException` → 400 с описанием ошибок валидации.

---

## Этап 4. Конфигурация (application.yml)

Создай `src/main/resources/application.yml` (можно удалить `application.properties`):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:taskmanager}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: update    # для учебного проекта ок; в проде используют миграции (Flyway/Liquibase)
    show-sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true     # включает /actuator/health/liveness и /readiness

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

**Важно:** значения вынесены через переменные окружения (`${DB_HOST:localhost}`) — это понадобится позже в Docker и Kubernetes, где база будет на другом хосте.

---

## Этап 5. Локальный запуск и проверка

Поставь PostgreSQL локально через Docker (это удобнее, чем устанавливать саму БД):

```bash
docker run --name pg-taskmanager -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=taskmanager -p 5432:5432 -d postgres:16
```

Запусти приложение:
```bash
./gradlew bootRun
```

Проверь:
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Создай задачу через Swagger UI или curl:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Изучить Docker","description":"Multi-stage build","status":"TODO"}'
```

✅ **Чекпоинт 1:** API работает локально, CRUD функционирует, Swagger открывается.

---

## Этап 6. Тесты

Минимум для учебного проекта:
- `TaskServiceTest` — юнит-тест с Mockito (`@Mock` репозиторий)
- `TaskControllerTest` — `@WebMvcTest` + `MockMvc`
- (опционально) интеграционный тест с **Testcontainers** — поднимает реальный Postgres в контейнере для теста

```bash
./gradlew test
```

✅ **Чекпоинт 2:** `./gradlew test` зелёный.

---

## Этап 7. Dockerfile (multi-stage build)

Создай `Dockerfile` в корне проекта:

```dockerfile
# ---- Stage 1: Build ----
FROM gradle:8.10-jdk17-alpine AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Зачем multi-stage:** в первой стадии нужен весь Gradle + JDK (тяжёлый образ), но в финальный образ это не нужно — там только JRE + готовый jar. Итоговый образ получается в разы меньше и без инструментов сборки (меньше поверхность атаки).

Собери и проверь:
```bash
docker build -t task-manager:1.0 .
docker images | grep task-manager   # глянь размер образа
```

---

## Этап 8. docker-compose (приложение + база вместе)

Создай `docker-compose.yml` в корне:

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: taskmanager
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
      DB_NAME: taskmanager
      DB_USER: postgres
      DB_PASSWORD: postgres
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
```

Запуск:
```bash
docker compose up --build
```

✅ **Чекпоинт 3:** приложение и база поднимаются одной командой, Swagger доступен на :8080.

---

## Этап 9. Kubernetes — локальный кластер

### 9.1 Выбор инструмента
- **Minikube** — рекомендую для первого знакомства, есть дашборд и аддоны
- **kind** — лёгче и быстрее, понадобится позже для CI/CD

Установи Minikube и запусти:
```bash
minikube start --driver=docker --memory=4096 --cpus=2
minikube addons enable ingress
minikube addons enable metrics-server
kubectl get nodes      # проверка, что кластер живой
```

### 9.2 Манифесты — создай папку `k8s/` в проекте

**k8s/configmap.yaml** — нечувствительные настройки:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: task-manager-config
data:
  DB_HOST: "postgres-service"
  DB_PORT: "5432"
  DB_NAME: "taskmanager"
```

**k8s/secret.yaml** — пароль (в реальности базируется через `kubectl create secret`, но для учёбы можно и yaml):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: task-manager-secret
type: Opaque
stringData:
  DB_USER: postgres
  DB_PASSWORD: postgres
```

**k8s/postgres-deployment.yaml** — база данных как Deployment + Service:
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
                configMapKeyRef: { name: task-manager-config, key: DB_NAME }
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef: { name: task-manager-secret, key: DB_USER }
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: task-manager-secret, key: DB_PASSWORD }
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

**k8s/app-deployment.yaml** — твоё приложение:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: task-manager
spec:
  replicas: 2
  selector:
    matchLabels:
      app: task-manager
  template:
    metadata:
      labels:
        app: task-manager
    spec:
      containers:
        - name: task-manager
          image: task-manager:1.0
          imagePullPolicy: Never   # т.к. образ собран локально в Minikube
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef: { name: task-manager-config }
            - secretRef: { name: task-manager-secret }
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
  name: task-manager-service
spec:
  type: NodePort
  selector:
    app: task-manager
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

### 9.3 Сборка образа прямо внутри Minikube

Важный трюк — чтобы Minikube видел локально собранный образ без registry:

```bash
eval $(minikube docker-env)      # переключает Docker CLI на демон внутри Minikube
docker build -t task-manager:1.0 .
```

### 9.4 Деплой

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/app-deployment.yaml

kubectl get pods -w        # смотри, как поды переходят в Running
kubectl get svc

minikube service task-manager-service --url   # получишь URL для проверки
```

✅ **Чекпоинт 4:** приложение крутится в Kubernetes, доступно по NodePort, поды устойчивы (liveness/readiness работают).

Полезные команды для практики и дебага:
```bash
kubectl logs <pod-name>
kubectl describe pod <pod-name>
kubectl exec -it <pod-name> -- sh
kubectl scale deployment task-manager --replicas=3
```

---

## Этап 10. CI/CD — GitHub Actions

### 10.1 Подготовка
1. Создай репозиторий на GitHub, запушь проект.
2. Registry — используем **GitHub Container Registry (ghcr.io)**: бесплатный, токен `GITHUB_TOKEN` создаётся автоматически в Actions, отдельная регистрация не нужна.

### 10.2 Пайплайн — создай `.github/workflows/ci-cd.yaml`

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
      - name: Deploy manifests
        run: |
          kubectl apply -f k8s/configmap.yaml
          kubectl apply -f k8s/secret.yaml
          kubectl apply -f k8s/postgres-deployment.yaml
          sed -i "s|image: task-manager:1.0|image: ${{ env.IMAGE_NAME }}:${{ github.sha }}|" k8s/app-deployment.yaml
          kubectl apply -f k8s/app-deployment.yaml
          kubectl wait --for=condition=ready pod -l app=task-manager --timeout=120s
      - name: Smoke test
        run: |
          kubectl port-forward svc/task-manager-service 8080:8080 &
          sleep 5
          curl -f http://localhost:8080/actuator/health
```

Что этот пайплайн делает по шагам:
1. **test** — на каждый push/PR гоняет юнит-тесты
2. **build-and-push** — только на `main`, после успешных тестов: собирает Docker-образ и пушит в `ghcr.io`
3. **deploy-to-kind** — поднимает временный kind-кластер прямо в CI, разворачивает туда приложение и делает smoke-test (проверка `/actuator/health`) — это имитация "деплоя", безопасная для обучения, без реального продакшен-кластера

✅ **Чекпоинт 5 (финал):** пуш в `main` → тесты → сборка образа → публикация в ghcr.io → деплой в эфемерный кластер → проверка health. Весь цикл DevOps замкнут.

---

## Что писать в резюме/портфолио по итогам

- Docker: multi-stage build, уменьшение размера образа, docker-compose для локальной среды
- Kubernetes: Deployment, Service, ConfigMap, Secret, liveness/readiness probes, масштабирование
- CI/CD: GitHub Actions, автоматические тесты, сборка и публикация образа в container registry, автоматический деплой в кластер

---

## Если что-то пойдёт не так

- `kubectl describe pod <name>` и `kubectl logs <name>` — первое, что смотреть при CrashLoopBackOff
- Если под не видит образ — проверь `imagePullPolicy: Never` и что собирал образ именно в `minikube docker-env`
- Если БД не подключается — проверь, что Service `postgres-service` резолвится (`kubectl exec -it <app-pod> -- nslookup postgres-service`)
- Возвращайся ко мне на любом этапе — разберём конкретную ошибку/лог
