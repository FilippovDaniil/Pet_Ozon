package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.request.UpdateOrderStatusRequest;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.service.InvoiceService;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Административные эндпоинты — только для роли ADMIN.
 *
 * Доступ контролируется в SecurityConfig:
 *   .requestMatchers("/api/admin/**").hasRole("ADMIN")
 * Spring проверяет роль ПЕРЕД вызовом метода контроллера.
 * Если роль не совпадает — автоматически 403 Forbidden.
 *
 * Группы операций:
 *   /api/admin/products  — CRUD товаров (без привязки к продавцу)
 *   /api/admin/orders    — просмотр и изменение статуса любых заказов
 *   /api/admin/invoices  — просмотр всех счетов
 */
@RestController
@RequestMapping(value = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;

    // --- Управление товарами ---

    // @ResponseStatus(CREATED) — Spring автоматически вернёт HTTP 201 вместо 200.
    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    // PUT — полная замена ресурса (все поля обновляются).
    @PutMapping(value = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    // NO_CONTENT (204) — стандартный ответ при успешном удалении (тело пустое).
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    // --- Управление заказами ---

    @GetMapping("/orders")
    public Page<OrderResponse> getAllOrders(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.getAllOrders(pageable);
    }

    /** Смена статуса заказа: CREATED → PAID, PAID → DELIVERED, CREATED → CANCELLED и т.д. */
    @PutMapping(value = "/orders/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.getStatus());
    }

    // --- Просмотр счетов ---

    @GetMapping("/invoices")
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }
}
