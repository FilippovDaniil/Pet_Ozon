package com.example.marketplace.controller;

import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Публичный каталог товаров.
 *
 * Все методы доступны БЕЗ аутентификации — настроено в SecurityConfig:
 *   .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
 *
 * Создание/редактирование/удаление товаров — в AdminController и SellerController.
 *
 * produces = APPLICATION_JSON_VALUE — явно указывает, что контроллер отдаёт JSON.
 * Без этого Spring всё равно вернёт JSON (если Jackson в classpath), но указание
 * делает поведение явным и предотвращает неожиданный XML.
 */
@RestController
@RequestMapping(value = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/products — постраничный список товаров с фильтрацией.
     *
     * Параметры (все необязательные):
     *   ?name=ноут         — поиск по названию (LIKE, без учёта регистра)
     *   ?category=Аудио    — фильтр по категории
     *   ?minPrice=1000     — минимальная цена
     *   ?maxPrice=50000    — максимальная цена
     *   ?page=0&size=20    — пагинация
     *   ?sort=price,asc    — сортировка
     *
     * @PageableDefault(size = 20) — если клиент не указал size, берём 20.
     * Pageable Spring формирует автоматически из query-параметров.
     * Page<T> в ответе содержит: content (элементы), totalElements, totalPages, number.
     */
    @GetMapping
    public Page<ProductResponse> getAllProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {
        return productService.getAllProducts(name, category, minPrice, maxPrice, pageable);
    }

    /**
     * GET /api/products/{id} — один товар по id.
     * @PathVariable извлекает {id} из URL-пути.
     */
    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }
}
