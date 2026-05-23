package com.example.marketplace.controller;

import com.example.marketplace.dto.request.AddToCartRequest;
import com.example.marketplace.dto.request.UpdateCartItemRequest;
import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер корзины.
 *
 * REST-эндпоинты:
 *   GET    /api/cart                  — просмотр корзины
 *   POST   /api/cart/items            — добавить товар в корзину
 *   PUT    /api/cart/items/{id}       — обновить количество позиции
 *   DELETE /api/cart/items/{id}       — удалить позицию (204 No Content)
 *
 * Оформление заказа вынесено в OrderController (POST /api/orders),
 * так как checkout создаёт новый ресурс — Order.
 */
@RestController
@RequestMapping(value = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal User user) {
        return cartService.getCartByUserId(user.getId());
    }

    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addToCart(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(user.getId(), request.getProductId(), request.getQuantity());
    }

    @PutMapping(value = "/items/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateCartItem(@PathVariable Long cartItemId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(cartItemId, request.getQuantity());
    }

    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromCart(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
    }
}
