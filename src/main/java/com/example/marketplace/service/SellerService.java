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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
@RequiredArgsConstructor
public class SellerService {

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
        return productService.toResponse(productRepository.save(product));
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
        return productService.toResponse(productRepository.save(product));
    }

    @PreAuthorize("hasRole('SELLER')")
    @Transactional
    public void deleteProduct(Long sellerId, Long productId) {
        Product product = resolveSellerProduct(sellerId, productId);
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
