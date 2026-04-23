package com.example.marketplace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа в приложение.
 *
 * @SpringBootApplication — «магическая» аннотация, которая включает сразу три:
 *   1. @Configuration     — этот класс может объявлять Spring-бины
 *   2. @EnableAutoConfiguration — Spring Boot сам настраивает компоненты (JPA, Security и т.д.)
 *   3. @ComponentScan     — Spring ищет все @Service, @Repository, @Controller в этом пакете и дочерних
 *
 * @Slf4j (Lombok) — автоматически создаёт поле: private static final Logger log = ...
 * Чтобы не писать его руками каждый раз.
 */
@SpringBootApplication
@Slf4j
public class MarketplaceApplication {

    public static void main(String[] args) {
        // SpringApplication.run запускает Spring-контейнер: создаёт все бины,
        // поднимает Tomcat на порту 8080, применяет миграции схемы БД.
        SpringApplication.run(MarketplaceApplication.class, args);
        log.info("=== Marketplace application started successfully! ===");
    }
}
