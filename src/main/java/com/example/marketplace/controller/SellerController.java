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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // @PageableDefault(size = 20) — если клиент не указал пагинацию, вернуть первые 20 товаров.
    @GetMapping("/products")
    public Page<ProductResponse> getMyProducts(@AuthenticationPrincipal User user,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return sellerService.getSellerProducts(user.getId(), pageable);
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

    /**
     * Загрузить изображение для товара.
     *
     * consumes = MULTIPART_FORM_DATA_VALUE — метод принимает только multipart/form-data
     * (не JSON). Это стандартный способ передачи файлов через HTTP.
     *
     * @RequestParam("file") MultipartFile file — Spring извлекает часть формы с именем "file".
     * MultipartFile — обёртка Spring над загруженным файлом: даёт доступ к байтам,
     * имени, размеру и Content-Type файла.
     *
     * Пример запроса через curl:
     *   curl -X POST http://localhost:8667/api/seller/products/1/image \
     *     -H "Authorization: Bearer <токен>" \
     *     -F "file=@/path/to/photo.jpg"
     */
    @PostMapping(value = "/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse uploadProductImage(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @RequestParam("file") MultipartFile file) {
        return sellerService.uploadProductImage(user.getId(), id, file);
    }

    /**
     * Удалить изображение товара.
     * После вызова поля imageData и imageContentType станут null.
     * Возвращает 204 No Content — стандарт для DELETE без тела ответа.
     */
    @DeleteMapping("/products/{id}/image")
    public ResponseEntity<Void> deleteProductImage(@AuthenticationPrincipal User user,
                                                    @PathVariable Long id) {
        sellerService.deleteProductImage(user.getId(), id);
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
