package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Бизнес-логика для работы с каталогом товаров.
 *
 * Главная особенность: метод getAllProducts использует JPA Specification
 * для динамической фильтрации. Specification — это функциональный интерфейс,
 * который строит условие WHERE для SQL-запроса.
 *
 * Методы toResponse / findEntityById объявлены public, чтобы
 * SellerService мог их переиспользовать без дублирования кода.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Возвращает постранично список товаров с опциональными фильтрами.
     *
     * Specification.where(null) — начало цепочки без условий (эквивалент "1=1").
     * Каждый фильтр добавляется через .and(...) только если параметр передан.
     *
     * Лямбда (root, q, cb) → ... — это Predicate:
     *   root — ссылка на Product-класс в JPQL (аналог таблицы),
     *   cb   — CriteriaBuilder, фабрика условий (like, equal, lessThan и т.д.).
     */
    public Page<ProductResponse> getAllProducts(String name, String category,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Pageable pageable) {
        Specification<Product> spec = Specification.where(null);
        if (name != null && !name.isBlank()) {
            // LIKE '%name%' без учёта регистра
            spec = spec.and((root, q, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("category"), category));
        }
        if (minPrice != null) {
            spec = spec.and((root, q, cb) ->
                    cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, q, cb) ->
                    cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        // Page<Product> → Page<ProductResponse>: .map() применяет toResponse к каждому элементу.
        return productRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public ProductResponse getProductById(Long id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(request.getCategory());
        ProductResponse response = toResponse(productRepository.save(product));
        log.info("Создан товар id={} «{}» price={}", response.getId(), response.getName(), response.getPrice());
        return response;
    }

    @Transactional
    public ProductResponse updateProduct(Long id, CreateProductRequest request) {
        Product product = findEntityById(id);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(request.getCategory());
        log.info("Обновлён товар id={}", id);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        log.info("Удалён товар id={}", id);
    }

    /** Возвращает JPA-сущность (а не DTO) — используется внутри приложения. */
    public Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    /**
     * Конвертирует JPA-сущность Product в DTO ProductResponse.
     *
     * Зачем конвертировать? Прямая отдача сущности через REST проблематична:
     *   1. Circular references (Product → User → Cart → CartItem → Product...) вызывают StackOverflow.
     *   2. В ответе видны все поля, включая внутренние.
     *   3. Ленивые поля (@ManyToOne LAZY) вызовут LazyInitializationException за пределами транзакции.
     * DTO — «плоский» объект только с нужными полями.
     */
    public ProductResponse toResponse(Product product) {
        ProductResponse r = new ProductResponse();
        r.setId(product.getId());
        r.setName(product.getName());
        r.setDescription(product.getDescription());
        r.setPrice(product.getPrice());
        r.setStockQuantity(product.getStockQuantity());
        r.setImageUrl(product.getImageUrl());
        r.setCategory(product.getCategory());
        if (product.getSeller() != null) {
            r.setSellerId(product.getSeller().getId());
            r.setSellerName(product.getSeller().getFullName());
            r.setShopName(product.getSeller().getShopName());
        }
        return r;
    }
}
