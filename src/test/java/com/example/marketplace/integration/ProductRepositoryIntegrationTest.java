package com.example.marketplace.integration;

import com.example.marketplace.entity.Product;
import com.example.marketplace.repository.ProductRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест репозитория товаров с реальной PostgreSQL.
 *
 * Чем отличается от юнит-тестов (CartServiceTest и др.)?
 *   • Юнит-тест: все зависимости — заглушки (Mockito @Mock). Нет БД, нет сети.
 *     Быстро, изолированно, но не проверяет реальные SQL-запросы.
 *   • Интеграционный тест: реальная БД в Docker-контейнере. Проверяет,
 *     что JPA-маппинг, SQL и схема таблиц работают корректно.
 *
 * @DataJpaTest — «срезовый» тест: загружает только JPA-слой (Entity + Repository).
 *   Контроллеры, сервисы, SecurityConfig — НЕ запускаются. Тест быстрее @SpringBootTest.
 *
 * @Testcontainers — JUnit 5 extension от Testcontainers.
 *   Управляет жизненным циклом контейнера: запускает Docker перед первым тестом,
 *   останавливает после последнего.
 *
 * @Container + static — один контейнер на весь класс (не пересоздаётся перед каждым тестом).
 *   Это быстрее, чем пересоздавать PostgreSQL на каждый метод.
 *
 * @ServiceConnection (Spring Boot 3.1+) — «магия» Boot:
 *   автоматически читает URL/логин/пароль из запущенного контейнера
 *   и переопределяет DataSource. Раньше для этого писали @DynamicPropertySource.
 *
 * @AutoConfigureTestDatabase(replace = NONE) — НЕ подменять DataSource встроенной H2.
 *   По умолчанию @DataJpaTest подключает H2 в памяти. Без NONE наш PostgreSQL игнорируется.
 */
// @Tag("integration") — помечает тест как интеграционный.
// build.gradle исключает эти тесты из обычного запуска ./gradlew test.
// Для запуска: ./gradlew integrationTest (требует запущенный Docker Desktop).
@Tag("integration")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryIntegrationTest {

    // PostgreSQL 15 в Alpine-образе (~80 МБ) — лёгкий вариант для тестов.
    // static: контейнер живёт столько же, сколько класс теста (не пересоздаётся).
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    ProductRepository productRepository;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Product makeProduct(String name, BigDecimal price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setStockQuantity(stock);
        // seller = null: поле необязательное, товар может существовать без продавца
        return p;
    }

    // ── тесты ─────────────────────────────────────────────────────────────────

    @Test
    void saveAndFindById_persistsAllFields() {
        // Сохраняем товар в реальную PostgreSQL через JPA.
        Product saved = productRepository.save(makeProduct("Ноутбук", new BigDecimal("79999.99"), 10));

        // После save() JPA назначает id через SEQUENCE (IDENTITY стратегия).
        assertThat(saved.getId()).isNotNull();

        // Перечитываем из БД — проверяем, что все поля сохранились корректно.
        Product found = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Ноутбук");
        assertThat(found.getPrice()).isEqualByComparingTo("79999.99"); // BigDecimal: игнорируем масштаб
        assertThat(found.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void findAll_returnsAllSavedProducts() {
        productRepository.save(makeProduct("Мышь",       new BigDecimal("1999.99"), 50));
        productRepository.save(makeProduct("Клавиатура", new BigDecimal("3499.99"), 30));

        List<Product> all = productRepository.findAll();

        // hasSizeGreaterThanOrEqualTo: тест устойчив к другим тестам, которые тоже сохраняют данные
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteById_removesProduct() {
        Product saved = productRepository.save(makeProduct("Наушники", new BigDecimal("5999.99"), 15));
        Long id = saved.getId();

        productRepository.deleteById(id);

        // findById должен вернуть Optional.empty() — товар удалён
        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void updateStockQuantity_persistsChange() {
        // Проверяет, что уменьшение остатка (как при оформлении заказа) реально сохраняется в БД.
        Product product = productRepository.save(makeProduct("Монитор", new BigDecimal("29999.99"), 5));

        product.setStockQuantity(2); // списываем 3 штуки
        productRepository.save(product);

        // Перечитываем — JPA не должна кэшировать старое значение
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void save_withZeroStock_isValid() {
        // Ноль на складе — допустимое состояние (товар закончился, но не удалён).
        Product outOfStock = productRepository.save(makeProduct("Вебкамера", new BigDecimal("2500.00"), 0));

        assertThat(outOfStock.getId()).isNotNull();
        assertThat(outOfStock.getStockQuantity()).isEqualTo(0);
    }
}
