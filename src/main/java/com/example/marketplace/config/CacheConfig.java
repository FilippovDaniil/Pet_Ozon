package com.example.marketplace.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Spring Cache — декларативное кэширование через аннотации.
 *
 * @EnableCaching — включает поддержку аннотаций @Cacheable, @CachePut, @CacheEvict.
 * Без этой аннотации все кэш-аннотации в сервисах просто игнорируются.
 *
 * ── Что такое Spring Cache? ───────────────────────────────────────────────────
 * Кэш — промежуточный слой хранения «дорогих» результатов.
 * Если метод уже был вызван с такими же аргументами — возвращаем сохранённый результат
 * вместо повторного SQL-запроса к базе данных.
 *
 * Пример: GET /api/products/1 вызывается 1000 раз в секунду.
 * Без кэша — 1000 SELECT запросов к БД в секунду.
 * С кэшем  — 1 запрос к БД, остальные 999 — из памяти.
 *
 * ── Как выбирается реализация кэша? ──────────────────────────────────────────
 * Spring Cache — это абстракция: один и тот же код работает с разными бэкендами.
 * Для учебного проекта используем ConcurrentMapCacheManager (простой HashMap в памяти).
 * В продакшне его заменяют на Redis, Caffeine или Hazelcast — без изменения кода сервисов.
 *
 * ── Именованные кэши ─────────────────────────────────────────────────────────
 * "products"      — кэш для одного товара по id (ProductService.getProductById)
 * "productsCatalog" — кэш для страниц каталога с фильтрами (ProductService.getAllProducts)
 *
 * Разные кэши позволяют инвалидировать их независимо:
 * при удалении товара очищаем оба кэша, чтобы ни один стало не устаревшим.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // ConcurrentMapCacheManager — простейшая реализация: ConcurrentHashMap<key, value>.
        // Данные хранятся в памяти JVM, пропадают при перезапуске.
        // Плюс: нет внешних зависимостей (Redis, Memcached).
        // Минус: не работает в кластере из нескольких инстанций приложения.
        return new ConcurrentMapCacheManager("products", "productsCatalog");
    }
}
