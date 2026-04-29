# ── Стадия 1: сборка ──────────────────────────────────────────────────────────
# Многоэтапная сборка (multi-stage build): в финальный образ попадает только JRE + JAR,
# без исходников, Gradle wrapper и кэша зависимостей. Итоговый образ ~200 МБ вместо ~800 МБ.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Копируем Gradle wrapper и build-файлы отдельным слоем.
# Если они не изменились, Docker переиспользует закэшированный слой с зависимостями.
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Скачиваем зависимости заранее (используем --no-daemon для меньшего потребления памяти).
RUN ./gradlew dependencies --no-daemon

# Копируем исходный код и собираем JAR (без тестов — они требуют БД или Docker).
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Стадия 2: runtime ─────────────────────────────────────────────────────────
# JRE вместо JDK: не нужен компилятор, меньше размер образа.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем только собранный JAR из предыдущей стадии.
COPY --from=builder /app/build/libs/*.jar app.jar

# 8080 — стандартный порт Spring Boot (задаётся в application.properties).
EXPOSE 8080

# ENTRYPOINT задаёт команду запуска контейнера.
# exec-форма (JSON) предпочтительна: процесс получает PID 1 и корректно обрабатывает SIGTERM.
ENTRYPOINT ["java", "-jar", "app.jar"]
