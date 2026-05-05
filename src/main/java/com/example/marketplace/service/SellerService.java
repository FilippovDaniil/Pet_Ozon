package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Бизнес-логика для продавца.
 *
 * Продавец управляет только своими товарами.
 * Ключевая безопасность: методы resolveSellerProduct и resolveSeller
 * проверяют, что продавец работает именно со своими данными.
 * Это называется «авторизация на уровне данных» (row-level security).
 *
 * ProductService переиспользуется здесь через делегирование:
 * productService.toResponse() и productService.findEntityById()
 * — не дублируем код конвертации.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerService {

    // Максимальный допустимый размер изображения: 2 МБ в байтах.
    // 1024 * 1024 = 1 МБ, умножаем на 2 → 2 МБ.
    private static final long MAX_IMAGE_SIZE_BYTES = 2L * 1024 * 1024;

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;  // делегируем конвертацию в ProductService

    /**
     * Возвращает товары данного продавца постранично.
     *
     * Page<T> — обёртка: content (элементы страницы), totalElements, totalPages, number.
     * Клиент передаёт ?page=0&size=20&sort=price,asc — Spring собирает это в Pageable.
     * .map() применяет toResponse к каждому Product внутри страницы без распаковки.
     */
    @PreAuthorize("hasRole('SELLER')")
    public Page<ProductResponse> getSellerProducts(Long sellerId, Pageable pageable) {
        User seller = resolveSeller(sellerId);
        return productRepository.findBySeller(seller, pageable).map(productService::toResponse);
    }

    /** Продавец создаёт новый товар — он автоматически привязывается к этому продавцу. */
    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public ProductResponse createProduct(Long sellerId, CreateProductRequest request) {
        User seller = resolveSeller(sellerId);
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setSeller(seller);
        ProductResponse response = productService.toResponse(productRepository.save(product));
        log.info("ACTION=SELLER_CREATE_PRODUCT sellerId={} productId={} name=\"{}\" price={}",
                sellerId, response.getId(), response.getName(), response.getPrice());
        return response;
    }

    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long productId, CreateProductRequest request) {
        // resolveSellerProduct проверяет: товар существует И принадлежит этому продавцу.
        Product product = resolveSellerProduct(sellerId, productId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        ProductResponse response = productService.toResponse(productRepository.save(product));
        log.info("ACTION=SELLER_UPDATE_PRODUCT sellerId={} productId={} name=\"{}\" price={}",
                sellerId, productId, response.getName(), response.getPrice());
        return response;
    }

    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public void deleteProduct(Long sellerId, Long productId) {
        Product product = resolveSellerProduct(sellerId, productId);
        log.info("ACTION=SELLER_DELETE_PRODUCT sellerId={} productId={} name=\"{}\"",
                sellerId, productId, product.getName());
        productRepository.delete(product);
    }

    /** Возвращает баланс продавца — сколько он заработал на продажах. */
    @PreAuthorize("hasRole('SELLER')")
    public SellerResponse getBalance(Long sellerId) {
        User seller = resolveSeller(sellerId);
        SellerResponse r = new SellerResponse();
        r.setId(seller.getId());
        r.setEmail(seller.getEmail());
        r.setFullName(seller.getFullName());
        r.setShopName(seller.getShopName());
        r.setBalance(seller.getBalance());
        return r;
    }

    /**
     * Загружает изображение для товара продавца.
     *
     * Принимает файл из multipart/form-data, проверяет его,
     * кодирует в Base64 и сохраняет в поле imageData сущности Product.
     *
     * Почему Base64? Это учебный проект. В реальном сервисе файлы хранят
     * в объектном хранилище (S3, MinIO), а в БД — только URL.
     * Base64 увеличивает размер файла примерно на 33%: 2 МБ → ~2.7 МБ в БД.
     *
     * @param file — файл из запроса (multipart/form-data, поле "file")
     */
    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public ProductResponse uploadProductImage(Long sellerId, Long productId, MultipartFile file) {
        // Проверка 1: файл не должен быть пустым.
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл изображения не должен быть пустым");
        }

        // Проверка 2: разрешаем только файлы с MIME-типом image/*.
        // getContentType() возвращает заголовок Content-Type из multipart-части.
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Разрешены только файлы изображений (image/jpeg, image/png и т.д.)");
        }

        // Проверка 3: ограничение по размеру (2 МБ).
        // Spring также ограничивает через spring.servlet.multipart.max-file-size,
        // но здесь проверяем ещё раз на уровне бизнес-логики.
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Размер изображения не должен превышать 2 МБ");
        }

        // Проверяем владение товаром — продавец может менять только свои товары.
        Product product = resolveSellerProduct(sellerId, productId);

        try {
            // file.getBytes() — читает содержимое файла в массив байт.
            // Base64.getEncoder().encodeToString() — преобразует байты в строку Base64.
            // Именно эту строку клиент подставит в тег: <img src="data:image/jpeg;base64,...">
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            product.setImageData(base64);
            product.setImageContentType(contentType);
        } catch (IOException e) {
            // IOException может возникнуть при чтении тела multipart-запроса.
            throw new RuntimeException("Не удалось прочитать файл изображения: " + e.getMessage(), e);
        }

        ProductResponse response = productService.toResponse(productRepository.save(product));
        log.info("ACTION=SELLER_UPLOAD_IMAGE sellerId={} productId={} contentType={} sizeBytes={}",
                sellerId, productId, contentType, file.getSize());
        return response;
    }

    /**
     * Удаляет изображение товара, обнуляя поля imageData и imageContentType.
     * После этого product.getImageData() вернёт null.
     */
    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public void deleteProductImage(Long sellerId, Long productId) {
        Product product = resolveSellerProduct(sellerId, productId);
        // Обнуляем оба поля — в БД запишется NULL.
        product.setImageData(null);
        product.setImageContentType(null);
        productRepository.save(product);
        log.info("ACTION=SELLER_DELETE_IMAGE sellerId={} productId={}", sellerId, productId);
    }

    /** Возвращает заказы, содержащие товары этого продавца. */
    @PreAuthorize("hasRole('SELLER')")
    public List<Order> getSellerOrders(Long sellerId) {
        resolveSeller(sellerId);
        return orderRepository.findBySellerId(sellerId);
    }

    /**
     * Загружает пользователя и проверяет, что он является продавцом.
     * Двойная проверка: и существование, и роль.
     */
    private User resolveSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + sellerId));
        if (user.getRole() != Role.SELLER) {
            throw new IllegalArgumentException("User " + sellerId + " is not a seller");
        }
        return user;
    }

    /**
     * Загружает товар и проверяет, что он принадлежит данному продавцу.
     * Без этой проверки продавец мог бы изменить чужой товар, зная его id.
     */
    private Product resolveSellerProduct(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (product.getSeller() == null || !product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("Product does not belong to this seller");
        }
        return product;
    }
}
