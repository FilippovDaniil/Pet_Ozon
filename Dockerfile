# Минимальный образ с JRE 21 (alpine). JDK не нужен — JAR уже собран на хосте.
FROM eclipse-temurin:21-jre-alpine
# Рабочая директория внутри контейнера.
WORKDIR /app
# Копируем готовый fat-jar (gradle bootJar выполняется ДО docker build — надёжнее, чем сборка внутри).
COPY build/libs/*.jar app.jar
# Порт приложения (совпадает с server.port=8667).
EXPOSE 8667
# Запуск Spring Boot приложения.
ENTRYPOINT ["java", "-jar", "app.jar"]
