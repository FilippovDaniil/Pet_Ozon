package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для продавцов — доступен только роли SELLER.
 *
 * Доступ контролируется в SecurityConfig:
 *   .requestMatchers("/api/seller/**").hasRole("SELLER")
 *
 * @AuthenticationPrincipal User user — Spring Security передаёт текущего
 * аутентифицированного пользователя. Его id передаётся в SellerService
 * для проверки владения товарами (защита от доступа к чужим данным).
 *
 * Эндпоинты:
 *   GET    /api/seller/products         — мои товары
 *   POST   /api/seller/products         — создать товар
 *   PUT    /api/seller/products/{id}    — обновить свой товар
 *   DELETE /api/seller/products/{id}    — удалить свой товар
 *   GET    /api/seller/balance          — мой баланс
 *   GET    /api/seller/sales            — заказы с моими товарами
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;
    private final OrderService orderService;

    @GetMapping("/products")
    public List<ProductResponse> getMyProducts(@AuthenticationPrincipal User user) {
        return sellerService.getSellerProducts(user.getId());
    }

    // ResponseEntity.status(201) — то же, что @ResponseStatus(CREATED), но через fluent API.
    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@AuthenticationPrincipal User user,
                                                          @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(201).body(sellerService.createProduct(user.getId(), request));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@AuthenticationPrincipal User user,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CreateProductRequest request) {
        return sellerService.updateProduct(user.getId(), id, request);
    }

    // noContent() — возвращает 204 без тела (стандарт для DELETE).
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal User user,
                                               @PathVariable Long id) {
        sellerService.deleteProduct(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Баланс продавца — сколько денег поступило от покупок его товаров. */
    @GetMapping("/balance")
    public SellerResponse getBalance(@AuthenticationPrincipal User user) {
        return sellerService.getBalance(user.getId());
    }

    /**
     * Продажи — заказы, содержащие товары этого продавца.
     * orderService.toResponse() конвертирует Order в DTO.
     * stream().map() применяет конвертацию к каждому заказу в списке.
     */
    @GetMapping("/sales")
    public List<OrderResponse> getSales(@AuthenticationPrincipal User user) {
        return sellerService.getSellerOrders(user.getId()).stream()
                .map(orderService::toResponse)
                .collect(Collectors.toList());
    }
}
