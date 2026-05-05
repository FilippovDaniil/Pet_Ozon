package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;

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
    private final ReviewRepository  reviewRepository;

    /**
     * Возвращает постранично список товаров с опциональными фильтрами.
     *
     * Specification.where(null) — начало цепочки без условий (эквивалент "1=1").
     * Каждый фильтр добавляется через .and(...) только если параметр передан.
     *
     * Лямбда (root, q, cb) → ... — это Predicate:
     *   root — ссылка на Product-класс в JPQL (аналог таблицы),
     *   cb   — CriteriaBuilder, фабрика условий (like, equal, lessThan и т.д.).
     *
     * @Cacheable — кэшировать результат.
     * value = "productsCatalog" — имя кэша (из CacheConfig).
     * key = "#name+'-'+#category+'-'+#minPrice+'-'+#maxPrice+'-'+#pageable" — уникальный ключ
     * из всех фильтров и пагинации. Разные запросы хранятся в кэше по разным ключам.
     *
     * Когда кэш НЕ используется? Когда сработает @CacheEvict в методах записи.
     * Это называется «инвалидация кэша» — устаревшие данные выбрасываются.
     */
    @Cacheable(value = "productsCatalog",
               key = "#name + '-' + #category + '-' + #minPrice + '-' + #maxPrice + '-' + #pageable")
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

    /**
     * @Cacheable(value = "products", key = "#id") — кэшировать по id товара.
     * Первый вызов getProductById(1L) → SELECT к БД, результат сохраняется в кэш.
     * Второй вызов getProductById(1L) → результат берётся из кэша, БД не трогается.
     *
     * Spring реализует это через AOP-прокси: перед вызовом метода проверяет кэш.
     * Если ключ найден — возвращает закэшированное значение, не вызывая сам метод.
     */
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        return toResponse(findEntityById(id));
    }

    /**
     * @Caching — объединяет несколько кэш-аннотаций на одном методе.
     * При создании товара очищаем "productsCatalog" полностью (allEntries = true):
     * все страницы каталога стали устаревшими — нужно перечитать из БД.
     */
    // Второй уровень защиты: даже если запрос обошёл SecurityFilterChain,
    // Spring AOP проверит роль непосредственно перед вызовом метода.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "productsCatalog", allEntries = true)
    })
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(request.getCategory());
        ProductResponse response = toResponse(productRepository.save(product));
        log.info("ACTION=ADMIN_CREATE_PRODUCT productId={} name=\"{}\" price={}",
                response.getId(), response.getName(), response.getPrice());
        return response;
    }

    /**
     * При обновлении товара:
     *   - очищаем конкретную запись из "products" (этот товар обновился)
     *   - очищаем весь "productsCatalog" (он мог быть на любой странице с любым фильтром)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products",        key = "#id"),
            @CacheEvict(value = "productsCatalog", allEntries = true)
    })
    public ProductResponse updateProduct(Long id, CreateProductRequest request) {
        Product product = findEntityById(id);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(request.getCategory());
        log.info("ACTION=ADMIN_UPDATE_PRODUCT productId={} name=\"{}\" price={}",
                id, product.getName(), product.getPrice());
        return toResponse(productRepository.save(product));
    }

    /**
     * При удалении товара очищаем обе записи в кэшах.
     * После удаления getProductById(id) должен бросать 404, а не возвращать старый результат.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products",        key = "#id"),
            @CacheEvict(value = "productsCatalog", allEntries = true)
    })
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        log.info("ACTION=ADMIN_DELETE_PRODUCT productId={}", id);
    }

    // Максимальный допустимый размер изображения: 2 МБ в байтах (тот же лимит, что и у продавца).
    private static final long MAX_IMAGE_SIZE_BYTES = 2L * 1024 * 1024;

    /**
     * Загружает изображение для любого товара — без проверки владельца.
     * Доступно только администратору (@PreAuthorize на уровне сервиса).
     *
     * Логика та же, что в SellerService.uploadProductImage, но без проверки sellerId.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products",        key = "#productId"),
            @CacheEvict(value = "productsCatalog", allEntries = true)
    })
    public ProductResponse uploadProductImage(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл изображения не должен быть пустым");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Разрешены только файлы изображений (image/jpeg, image/png и т.д.)");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Размер изображения не должен превышать 2 МБ");
        }
        Product product = findEntityById(productId);
        try {
            // Base64-кодирование байт файла → строка для хранения в TEXT-колонке PostgreSQL
            product.setImageData(Base64.getEncoder().encodeToString(file.getBytes()));
            product.setImageContentType(contentType);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать файл изображения: " + e.getMessage(), e);
        }
        ProductResponse response = toResponse(productRepository.save(product));
        log.info("ACTION=ADMIN_UPLOAD_IMAGE productId={} contentType={} sizeBytes={}",
                productId, contentType, file.getSize());
        return response;
    }

    /**
     * Удаляет изображение товара — обнуляет оба поля.
     * Доступно только администратору.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products",        key = "#productId"),
            @CacheEvict(value = "productsCatalog", allEntries = true)
    })
    public void deleteProductImage(Long productId) {
        Product product = findEntityById(productId);
        product.setImageData(null);
        product.setImageContentType(null);
        productRepository.save(product);
        log.info("ACTION=ADMIN_DELETE_IMAGE productId={}", productId);
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
        // Копируем Base64-данные изображения и его MIME-тип в ответ.
        r.setImageData(product.getImageData());
        r.setImageContentType(product.getImageContentType());
        r.setCategory(product.getCategory());
        if (product.getSeller() != null) {
            r.setSellerId(product.getSeller().getId());
            r.setSellerName(product.getSeller().getFullName());
            r.setShopName(product.getSeller().getShopName());
        }

        // Подгружаем средний рейтинг и количество отзывов из БД.
        // getAverageRatingByProduct возвращает Double (null если отзывов нет).
        // countByProduct — SELECT COUNT(*), без загрузки всех строк в память.
        r.setAverageRating(reviewRepository.getAverageRatingByProduct(product));
        r.setReviewCount((int) reviewRepository.countByProduct(product));
        return r;
    }
}
