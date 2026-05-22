# rancher.md — Портативный гайд: Spring Boot → Rancher Desktop (Kubernetes)

> Этот файл создан для переиспользования в других проектах.
> Скопируй его в корень нового проекта и отдай Claude в начале сессии.
> Claude прочитает его и сразу приступит к настройке K8s-инфраструктуры.

---

## Что такое Rancher Desktop

Rancher Desktop — бесплатная альтернатива Docker Desktop для Windows/Mac/Linux.
Устанавливает локальный Kubernetes-кластер (k3s) + Docker + kubectl + Helm одним пакетом.
Идеален для локального тестирования Kubernetes-деплоев.

**Скачать:** https://rancherdesktop.io  
**При установке выбрать:** Container Runtime = `dockerd (Moby)`

---

## Архитектура Rancher Desktop — критически важно понять

```
┌─────────────────────────────────────────────────────────┐
│  Windows Host                                            │
│                                                          │
│  docker CLI ──────────────────────► Docker Desktop      │
│                  (отдельный демон!)   daemon             │
│                                                          │
│  ┌─── Rancher Desktop VM (Linux) ──────────────────┐    │
│  │                                                  │    │
│  │  Docker daemon ◄── k3s (--docker flag)          │    │
│  │  /var/run/docker.sock                            │    │
│  │                                                  │    │
│  │  kubectl / helm ────────────────────────────►   │    │
│  │                        K8s API :6443             │    │
│  └──────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

**Ключевая ловушка:** `docker build` кладёт образ в **Docker Desktop**.  
k3s использует Docker **внутри VM** — это два разных хранилища образов!  
`imagePullPolicy: Never` с `ErrImageNeverPull` = классический симптом этой проблемы.

---

## Паттерн загрузки образа в Rancher Desktop

```powershell
# Шаг 1: Собрать образ (--provenance=false обязателен!)
# Без флага BuildKit создаёт manifest list → k3s не находит его с imagePullPolicy: Never
docker build --provenance=false -t my-app:1.0.0 .

# Шаг 2: Сохранить в файл
docker save my-app:1.0.0 -o $env:TEMP\my-app.tar

# Шаг 3: Загрузить в Docker внутри VM Rancher Desktop
# /mnt/c/ = C:\ видимая из Linux VM через WSL
rdctl shell -- sh -c "docker load < /mnt/c/Users/$env:USERNAME/AppData/Local/Temp/my-app.tar"

# Проверка
rdctl shell -- sh -c "docker images my-app"
```

**В манифесте K8s всегда:**
```yaml
imagePullPolicy: Never   # Не тянуть из registry, использовать локальный образ
```

---

## Стандартная структура K8s манифестов

Файлы нумеруются для правильного порядка применения:

```
rancher/k8s/
├── 00-namespace.yaml     # Изолированный namespace (применяется первым)
├── 01-secrets.yaml       # Пароли и чувствительные данные
├── 02-postgres.yaml      # PostgreSQL: PVC + Deployment + Service
├── 03-loki.yaml          # Grafana Loki: ConfigMap + PVC + Deployment + Service
├── 04-prometheus.yaml    # Prometheus: ConfigMap + PVC + Deployment + Service
├── 05-grafana.yaml       # Grafana: 3×ConfigMap + PVC + Deployment + Service
├── 06-app.yaml           # Основное приложение: ConfigMap + Deployment + Service
└── 07-dashboard.yaml     # Kubernetes Dashboard: веб-интерфейс управления кластером
```

Применить всё:
```powershell
kubectl apply -f rancher/k8s/
```

---

## Правило именования сервисов

**Проблема:** Конфиги (logback, Prometheus, Grafana datasources) жёстко прописывают имена хостов.

| Где прописан хост | Имя хоста | Должно совпадать с |
|---|---|---|
| `logback-spring.xml` (loki4j URL) | `http://loki:3100` | K8s Service `name: loki` |
| `prometheus.yml` (scrape target) | `marketplace-service:8667` | K8s Service `name: marketplace-service` |
| Grafana datasource (loki.yml) | `http://loki:3100` | K8s Service `name: loki` |
| Grafana datasource (prometheus.yml) | `http://prometheus:9090` | K8s Service `name: prometheus` |

**Правило:** Называй K8s сервисы так же как docker-compose сервисы.  
Тогда один и тот же конфиг работает и в docker-compose и в K8s.

---

## Spring Boot: обязательные настройки для K8s

### ConfigMap (несекретные переменные)
```yaml
data:
  # URL БД через K8s DNS (не localhost!)
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres-service:5432/mydb"
  
  # Профиль для включения Loki appender
  SPRING_PROFILES_ACTIVE: "docker"
  
  # Отключить mail health indicator (без реального SMTP → 503)
  MANAGEMENT_HEALTH_MAIL_ENABLED: "false"
  
  # Показывать детали health endpoint (удобно для диагностики)
  MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: "always"
```

### Почему mail health indicator вызывает проблемы
Spring Boot автоконфигурирует MailHealthIndicator когда прописан spring.mail.
Без реального SMTP-пароля соединение не устанавливается → HealthIndicator = DOWN →
`/actuator/health` возвращает 503 → readinessProbe видит 503 → перезапускает под бесконечно.

**Всегда отключай:** `MANAGEMENT_HEALTH_MAIL_ENABLED: "false"` если SMTP не настроен.

### Секреты (пароли)
```yaml
# Secret: marketplace-secrets
stringData:
  postgres-password: "1234"
  mail-password: ""

# В Deployment ссылаемся:
env:
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: marketplace-secrets
        key: postgres-password
```

---

## Стандартный Deployment для Spring Boot

```yaml
spec:
  template:
    spec:
      # initContainer: ждём PostgreSQL перед стартом Spring Boot
      # Без этого: Connection refused → CrashLoopBackOff
      initContainers:
        - name: wait-for-postgres
          image: busybox:1.36
          command:
            - sh
            - -c
            - |
              until nc -z postgres-service 5432; do
                sleep 2
              done

      containers:
        - name: app
          image: my-app:1.0.0
          imagePullPolicy: Never    # Rancher Desktop: всегда Never для локальных образов
          
          # readinessProbe: когда Pod готов принимать трафик?
          # Пока probe не пройдёт — Service не роутит запросы на этот Pod.
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30   # Дать Spring Boot время запуститься
            periodSeconds: 10
            failureThreshold: 6       # 6×10 = 60 сек на прогрев
          
          # livenessProbe: жив ли Pod?
          # Если нет — K8s перезапускает контейнер.
          # initialDelaySeconds БОЛЬШЕ чем у readiness — не перезапускать во время старта!
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60   # Всегда > readiness.initialDelaySeconds
            periodSeconds: 15
            failureThreshold: 3
```

---

## NodePorts: стандартные значения

Диапазон NodePort: 30000–32767. Рекомендуемые значения (легко запомнить):

| Сервис | Внутренний порт | NodePort | URL |
|--------|-----------------|----------|-----|
| Приложение | 8667 | 30667 | http://localhost:30667 |
| Prometheus | 9090 | 30900 | http://localhost:30900 |
| Grafana | 3000 | 30300 | http://localhost:30300 |
| K8s Dashboard | 443 | 30443 | https://localhost:30443 |
| Loki | 3100 | — | ClusterIP (внутренний) |
| PostgreSQL | 5432 | — | ClusterIP (внутренний) |

---

## PVC (PersistentVolumeClaim): рекомендуемые размеры

| Сервис | Размер | Обоснование |
|--------|--------|-------------|
| PostgreSQL | 1–2 Gi | Учебный проект с ~250 товарами |
| Loki | 2 Gi | 7 дней логов |
| Prometheus | 1 Gi | 7 дней метрик |
| Grafana | 256 Mi | SQLite + плагины |

---

## Grafana: provisioning через ConfigMap

Grafana читает YAML-файлы из `/etc/grafana/provisioning/` при старте.
В K8s файлы монтируются из ConfigMap как директории.

```yaml
# Три ConfigMap для полной автоматической настройки:
# 1. grafana-datasources → /etc/grafana/provisioning/datasources/
# 2. grafana-dashboards-config → /etc/grafana/provisioning/dashboards/
# 3. grafana-dashboards (JSON) → /etc/grafana/provisioning/dashboards/json/

volumeMounts:
  - name: grafana-datasources
    mountPath: /etc/grafana/provisioning/datasources
  - name: grafana-dashboards-config
    mountPath: /etc/grafana/provisioning/dashboards
  - name: grafana-dashboards        # JSON файлы — в подпапку!
    mountPath: /etc/grafana/provisioning/dashboards/json
```

**Важно:** JSON дашборды монтируются в ПОДПАПКУ `/json`, а не в `/dashboards` напрямую.
Иначе они перезатрут `dashboards.yml` из предыдущего тома.

---

## Loki: securityContext

```yaml
# Loki 2.x запускается от UID 10001, но PVC создаётся с правами root.
# → Permission denied при попытке писать в /loki
# Решение: запустить от root (как в docker-compose: user: "0")
spec:
  securityContext:
    runAsUser: 0
```

---

## Kubernetes Dashboard — веб-интерфейс управления

Официальный UI для управления кластером прямо из браузера.

```yaml
# 07-dashboard.yaml устанавливается в отдельный namespace kubernetes-dashboard.
# Содержит 15 ресурсов: Namespace + RBAC + Deployment + NodePort Service + admin токен.
```

```powershell
# Установить Dashboard
kubectl apply -f rancher/k8s/07-dashboard.yaml

# Дождаться готовности
kubectl rollout status deployment/kubernetes-dashboard -n kubernetes-dashboard --timeout=120s
```

**Получить токен для входа (PowerShell):**
```powershell
$b64 = kubectl -n kubernetes-dashboard get secret admin-user-token -o jsonpath='{.data.token}'
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
```

**Войти:** https://localhost:30443 → выбрать "Token" → вставить токен.  
Браузер покажет предупреждение HTTPS (самоподписанный сертификат) — "Дополнительно" → "Перейти".

**Что умеет Dashboard:**

| Действие | Где |
|----------|-----|
| Просмотр подов и их статуса | Workloads → Pods |
| Логи контейнера | Pods → [под] → иконка логов |
| Перезапуск деплоймента | Workloads → Deployments → ⋮ → Restart |
| Остановить / масштабировать | Workloads → Deployments → ⋮ → Scale |
| События пода (почему не стартует) | Pods → [под] → Events |
| ConfigMap и Secret | Config → ConfigMaps / Secrets |

> Слева вверху — переключатель namespace. Выбирай `marketplace` для своего стека.

**Удалить Dashboard:**
```powershell
kubectl delete namespace kubernetes-dashboard
kubectl delete clusterrolebinding admin-user
```

---

## Команды для ежедневной работы

```powershell
# Статус всего стека
kubectl get all -n marketplace

# Логи приложения (live)
kubectl logs -n marketplace deployment/marketplace-app -f

# Описание пода — события, probe failures, ошибки образа
kubectl describe pod -n marketplace POD_NAME

# Войти внутрь контейнера
kubectl exec -it -n marketplace deployment/marketplace-app -- /bin/sh

# Прямой доступ к сервису без NodePort
kubectl port-forward -n marketplace service/marketplace-service 8667:8667

# Обновить образ
docker build --provenance=false -t my-app:1.0.0 .
docker save my-app:1.0.0 -o $env:TEMP\my-app.tar
rdctl shell -- sh -c "docker load < /mnt/c/Users/$env:USERNAME/AppData/Local/Temp/my-app.tar"
kubectl rollout restart deployment/marketplace-app -n marketplace

# Полный сброс
kubectl delete namespace marketplace

# Пересоздать
kubectl apply -f rancher/k8s/
```

---

## Диагностика частых проблем

### Pod в ErrImageNeverPull
```
Симптом: Pod не запускается, Events показывают ErrImageNeverPull
Причина: Образ есть в Docker Desktop, но не в VM Rancher Desktop
Решение: Загрузить образ в VM (см. "Паттерн загрузки образа" выше)
```

### /actuator/health возвращает 503
```
Симптом: readinessProbe failed: HTTP probe failed with statuscode: 503
Причина: Один из HealthIndicator DOWN (чаще всего — mail)
Решение: Добавить в ConfigMap: MANAGEMENT_HEALTH_MAIL_ENABLED: "false"
Диагностика: kubectl port-forward ..., затем GET /actuator/health
             Добавь MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: "always"
```

### Pod в CrashLoopBackOff
```
Симптом: Pod рестартует снова и снова
Причина: Spring Boot не смог подключиться к БД при старте
Решение: Проверить initContainer wait-for-postgres, увеличить initialDelaySeconds
Диагностика: kubectl logs POD_NAME --previous (логи предыдущего запуска)
```

### Metrics не появляются в Prometheus
```
Симптом: Prometheus показывает target как "down"
Причина: Неверное имя сервиса в prometheus.yml
Решение: Убедиться что targets: ['marketplace-service:8667'] совпадает с именем K8s Service
```

### Логи не появляются в Loki
```
Симптом: Grafana дашборды пустые, LogQL не находит данные
Причина 1: SPRING_PROFILES_ACTIVE не равен "docker" → loki4j appender не активен
Причина 2: Сервис Loki называется не "loki" → logback не может отправить логи
Решение: Проверить ConfigMap marketplace-config → SPRING_PROFILES_ACTIVE: "docker"
         Проверить Service name: loki в 03-loki.yaml
```

---

## Порядок запуска сервисов: initContainers

Когда в стеке есть зависимости (A ждёт B), используй initContainers — они выполняются строго по порядку до старта основного контейнера.

```yaml
initContainers:
  - name: wait-for-db
    image: busybox:1.36
    command:
      - sh
      - -c
      - until nc -z postgres 5432; do sleep 2; done
  - name: wait-for-kafka
    image: busybox:1.36
    command:
      - sh
      - -c
      - until nc -z kafka 29092; do sleep 2; done
```

**`nc -z host port`** — "zero-I/O mode": проверяет открытость TCP-порта без отправки данных.
Выходит с кодом 0 (успех) когда порт принимает соединения.

Типичный порядок для стека с Kafka:
```
ZooKeeper → Kafka → [Kafdrop, App]  (параллельно, но каждый с initContainer)
```

---

## Kafka в Kubernetes: конфликт KAFKA_PORT с K8s Service Links

**Проблема:** Если K8s Service называется `kafka`, то во всех подах того же namespace автоматически создаётся переменная `KAFKA_PORT=tcp://ip:port`. Confluent Kafka-образ интерпретирует **любую** переменную `KAFKA_*` как конфиг-параметр. `KAFKA_PORT` конфликтует с внутренней конфигурацией → Exit Code 1 через 2 секунды после старта.

**Решение:** `enableServiceLinks: false` в pod spec.

```yaml
spec:
  template:
    spec:
      enableServiceLinks: false   # Отключить инжекцию env-переменных K8s Services
      containers:
        - name: kafka
          ...
```

Это отключает автоматическую инжекцию переменных вида `<SERVICE_NAME>_PORT` для всех сервисов namespace в этот под. Kafka сможет стартовать с чистым окружением.

**Симптом:** Pod стартует, лог обрывается на `Running in Zookeeper mode...`, Exit Code 1 через ~2 секунды.

---

## Kafka в Kubernetes: dual listeners

Kafka должна сообщать клиентам свой **resolvable** адрес. В K8s:

```yaml
# Два listener'а обязательны:
KAFKA_LISTENERS: "PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092"
KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092"
```

| Listener | Кто использует |
|----------|---------------|
| `PLAINTEXT://kafka:29092` | Поды внутри K8s (K8s DNS-имя `kafka`) |
| `PLAINTEXT_HOST://localhost:9092` | Хост-машина через `kubectl port-forward` |

**Почему нельзя один?** Если advertised = `localhost:9092`, поды получают `localhost`
как адрес брокера — это resolves в их собственный loopback, не в Kafka → Connection refused.

---

## Grafana: три ConfigMap в одном деплойменте

Нельзя смонтировать два ConfigMap в одну директорию — второй полностью заменяет содержимое первого.

**Паттерн — подпапка для JSON:**
```
/etc/grafana/provisioning/dashboards/          ← ConfigMap A (dashboards.yml)
/etc/grafana/provisioning/dashboards/json/     ← ConfigMap B (*.json файлы)
```

В `dashboards.yml` указывать `path: /etc/grafana/provisioning/dashboards/json`.

```yaml
volumeMounts:
  - name: dashboard-config
    mountPath: /etc/grafana/provisioning/dashboards        # ConfigMap A
  - name: dashboard-json
    mountPath: /etc/grafana/provisioning/dashboards/json   # ConfigMap B (подпапка!)
```

Это работает потому что K8s монтирует подпапку как отдельный том поверх родительской директории, не заменяя весь `/dashboards/`.

---

## Helm chart: продвинутый деплой

После освоения plain kubectl переходи на Helm:

```powershell
# Проверить синтаксис
helm lint rancher/helm/marketplace

# Посмотреть что сгенерирует (без деплоя)
helm template marketplace rancher/helm/marketplace

# Установить (с переопределением пароля)
helm install marketplace rancher/helm/marketplace --set postgres.password=mypass

# Обновить
helm upgrade marketplace rancher/helm/marketplace --set app.tag=2.0.0

# Откатить
helm rollback marketplace 1

# Удалить
helm uninstall marketplace
```

---

## Promtail в Kubernetes (k3s / Rancher Desktop)

k3s пишет логи контейнеров в **CRI-формате**, а не в Docker JSON:
```
2024-01-01T10:00:00Z stdout F {"level":"INFO","service":"booking-service","message":"..."}
```

Поэтому pipeline stage в Promtail конфиге должен быть `cri: {}`, а не `docker: {}`:

```yaml
pipeline_stages:
  - cri: {}       # парсит CRI-формат k3s; docker: {} работает только с docker-compose
  - json:
      expressions:
        level: level
        service: service
  - labels:
      level:
      service:
```

Glob-паттерн для логов конкретного namespace (например pet-hotel):
```yaml
__path__: /var/log/pods/pet-hotel_*/*/*.log
```

### DaemonSet для Promtail

Promtail должен работать на каждом узле кластера. В Rancher Desktop один узел — control-plane.
Без `tolerations` DaemonSet не получит Pod на control-plane (узел помечен taint'ом).

```yaml
kind: DaemonSet
spec:
  template:
    spec:
      tolerations:
        # control-plane taint: node-role.kubernetes.io/control-plane = NoSchedule
        # Rancher Desktop = single-node кластер, этот узел является control-plane
        - key: node-role.kubernetes.io/control-plane
          operator: Exists
          effect: NoSchedule
        # master — legacy имя для control-plane в старых K8s версиях
        - key: node-role.kubernetes.io/master
          operator: Exists
          effect: NoSchedule
      securityContext:
        runAsUser: 0        # Нужен root для чтения /var/log/pods
        runAsGroup: 0
      containers:
        - name: promtail
          securityContext:
            privileged: true    # Доступ к Docker socket и системным файлам логов
```

---

## subPath: монтирование одного файла из ConfigMap

По умолчанию монтирование ConfigMap создаёт в директории **только** файлы из ConfigMap,
удаляя всё остальное. Для `init-db.sql` нельзя заменить всю директорию `/docker-entrypoint-initdb.d/`.

Решение — `subPath: <key>`: монтирует один файл, не директорию:

```yaml
volumes:
  - name: init-script
    configMap:
      name: postgres-init-config   # ConfigMap с ключом init-db.sql

containers:
  - name: postgres
    volumeMounts:
      - name: init-script
        # mountPath: полный путь файла в контейнере
        mountPath: /docker-entrypoint-initdb.d/init-db.sql
        # subPath: имя ключа в ConfigMap = только этот файл монтируется
        # Без subPath: вся директория /docker-entrypoint-initdb.d/ заменяется
        subPath: init-db.sql
```

**Ограничение subPath:** изменения в ConfigMap не применяются автоматически к смонтированному файлу.
Для production использовать immutable ConfigMap + явный rollout restart.

---

## Spring Kafka: lazy connection

Spring Boot автоматически настраивает Kafka из `spring.kafka` в classpath.
Но **KafkaTemplate не инициализируется при старте** — соединение открывается при первой отправке.

Это означает:
- Сервис **без KafkaTemplate** (например, support-service) **стартует без Kafka**.
- initContainer `wait-for-kafka` не нужен таким сервисам.
- Сервисы которые **реально используют** KafkaTemplate / @KafkaListener **должны** ждать Kafka.

```yaml
# Нужен wait-for-kafka:
#   booking-service   (publishesBookingCreatedEvent via KafkaTemplate)
#   billing-service   (@KafkaListener booking.created)
#   dining-service    (KafkaTemplate для order.created)
#
# НЕ нужен wait-for-kafka:
#   support-service   (Kafka в зависимостях, но нет KafkaTemplate/Listener)
#   customer-service  (только JWT, нет Kafka)
```

---

## @KafkaListener vs KafkaTemplate: кто требует wait-for-kafka

Два разных Kafka клиента — разное поведение при старте Spring Boot:

| | KafkaTemplate (producer) | @KafkaListener (consumer) |
|--|--|--|
| Соединение | **Lazy** — при первой отправке | **Eager** — сразу при старте |
| Без Kafka при старте | Стартует нормально | CrashLoopBackOff |
| initContainer нужен? | Нет | **Да, обязательно** |

```yaml
# initContainer wait-for-kafka нужен ТОЛЬКО если есть @KafkaListener:
initContainers:
  - name: wait-for-kafka
    image: busybox:1.36
    command: ["sh", "-c", "until nc -z kafka 9092; do sleep 2; done"]

# Если только KafkaTemplate (producer) — initContainer НЕ нужен.
# Spring Kafka откроет соединение при первом вызове kafkaTemplate.send().
```

Как определить нужен ли wait-for-kafka: поищи `@KafkaListener` в коде сервиса.
Если есть — нужен. Если только `KafkaTemplate.send()` — не нужен.

---

## Database-per-Service: несколько PostgreSQL в одном кластере

При микросервисной архитектуре каждый сервис имеет свою БД.
Паттерн: один YAML файл содержит N групп (PVC + Deployment + Service):

```yaml
# Для каждой БД — три ресурса:
# PVC: postgres-auth-pvc (500Mi)
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-auth-pvc
  namespace: cinema
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 500Mi
---
# Deployment: postgres-auth
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres-auth
  namespace: cinema
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres-auth
  template:
    metadata:
      labels:
        app: postgres-auth
    spec:
      containers:
        - name: postgres
          image: postgres:15-alpine
          env:
            - name: POSTGRES_DB
              value: auth_db     # ← уникальное имя БД
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef: { name: cinema-secrets, key: db-user }
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: cinema-secrets, key: db-password }
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "cinema", "-d", "auth_db"]
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 6
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres-auth-pvc
---
# Service: postgres-auth (DNS имя = docker-compose container name)
apiVersion: v1
kind: Service
metadata:
  name: postgres-auth   # ← микросервис использует DB_HOST=postgres-auth
  namespace: cinema
spec:
  selector:
    app: postgres-auth
  ports:
    - port: 5432
      targetPort: 5432
  type: ClusterIP
```

Повторяй блок для каждого сервиса, меняя: `auth` → `movie`, `hall`, `order`, etc.

**Ключевое правило:** Имя K8s Service (`name: postgres-auth`) должно совпадать
с именем контейнера в docker-compose. Тогда `DB_HOST=postgres-auth` работает в обеих средах.

---

## Gradle multi-module: контекст сборки в Docker

Gradle multi-module проект требует корень проекта как контекст сборки.
Иначе Dockerfile не найдёт `gradlew`, `settings.gradle.kts` и другие модули.

```powershell
# Неправильно — контекст только директория сервиса:
docker build -f auth-service/Dockerfile auth-service/
# Ошибка: COPY gradlew . → файл не найден

# Правильно — контекст = корень проекта:
docker build --provenance=false -f auth-service/Dockerfile .
```

Исключение: фронтенд (`frontend/`) — он независим, его контекст = `frontend/`:
```powershell
docker build --provenance=false -f frontend/Dockerfile frontend/
```

В PowerShell скрипте это реализуется через отдельное поле `Context` в таблице образов:
```powershell
$Images = [ordered]@{
    "cinema-auth-service" = @{ Dockerfile = "auth-service/Dockerfile"; Context = "." }
    "cinema-frontend"     = @{ Dockerfile = "Dockerfile";              Context = "frontend" }
}
```

---

## Адаптация для других проектов

### Что нужно изменить в манифестах:

1. **namespace** — в 00-namespace.yaml и во всех `namespace:` полях
2. **Имя образа и тег** — в 06-app.yaml: `image: my-app:1.0.0`
3. **Порт приложения** — `containerPort`, `port`, `targetPort`, `nodePort` в 06-app.yaml
4. **URL БД** — `SPRING_DATASOURCE_URL` в ConfigMap
5. **Имя БД** — `POSTGRES_DB` и в URL
6. **Пароли** — в 01-secrets.yaml

### Для микросервисной архитектуры:

Каждый микросервис = отдельный Deployment + Service.  
Нумерация: 06-service-a.yaml, 07-service-b.yaml, ...  
Или Helm chart с несколькими deployment шаблонами.

### Для проектов без Loki/Prometheus:

Пропусти 03-loki.yaml, 04-prometheus.yaml, 05-grafana.yaml.  
Убери `SPRING_PROFILES_ACTIVE: "docker"` из ConfigMap.  
Убери `depends_on: loki` если есть (это docker-compose концепт, не K8s).
