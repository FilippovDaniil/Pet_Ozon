package com.example.marketplace.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger / OpenAPI 3.
 *
 * @OpenAPIDefinition — задаёт метаданные всего API (заголовок, версию, описание).
 * Они отображаются в шапке страницы Swagger UI.
 *
 * @SecurityScheme — описывает схему безопасности «JWT Bearer».
 * После этого в Swagger UI появится кнопка «Authorize», где можно вставить токен.
 * Токен получается из POST /api/auth/login → поле «token» в ответе.
 *
 * name = "JWT" — идентификатор схемы. Контроллеры могут ссылаться на неё через
 * @SecurityRequirement(name = "JWT") чтобы пометить эндпоинт как «требует токен».
 *
 * Доступ к Swagger UI: http://localhost:8080/swagger-ui.html
 * Сырой JSON-спецификации: http://localhost:8080/v3/api-docs
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Pet_Ozon — Marketplace API",
        version = "1.0",
        description = "REST API учебного маркетплейса на Spring Boot 3.x. " +
                      "Для работы с защищёнными эндпоинтами нажмите «Authorize» и вставьте JWT-токен."
))
@SecurityScheme(
        name = "JWT",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Получите токен через POST /api/auth/login, затем вставьте его сюда."
)
public class OpenApiConfig {
    // Конфигурация задаётся аннотациями — дополнительный код не нужен.
}
