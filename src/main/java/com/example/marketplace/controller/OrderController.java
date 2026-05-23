package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CheckoutRequest;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.CartService;
import com.example.marketplace.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер заказов.
 *
 * REST-эндпоинты:
 *   POST /api/orders          — оформить заказ из корзины (201 Created)
 *   GET  /api/orders/my       — мои заказы (с пагинацией)
 *   GET  /api/orders/{id}     — конкретный заказ по id
 */
@RestController
@RequestMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;

    /**
     * POST /api/orders — оформление заказа.
     * Превращает содержимое корзины в Order + Invoice.
     * Возвращает 201 Created с созданным заказом.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@AuthenticationPrincipal User user,
                                     @Valid @RequestBody CheckoutRequest request) {
        return cartService.checkout(user.getId(), request.getShippingAddress());
    }

    @GetMapping("/my")
    public Page<OrderResponse> getMyOrders(@AuthenticationPrincipal User user,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.getOrdersByUserId(user.getId(), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }
}
