package com.example.marketplace.controller;

import com.example.marketplace.dto.request.AddToCartRequest;
import com.example.marketplace.dto.request.CheckoutRequest;
import com.example.marketplace.dto.request.UpdateCartItemRequest;
import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Временная заглушка: пользователь определяется по заголовку X-User-Id.
     * Если заголовок не передан — используется id=1 (первый пользователь).
     * После добавления Spring Security этот метод будет заменён на получение
     * пользователя из SecurityContext.
     */
    private Long resolveUserId(String xUserId) {
        if (xUserId != null && !xUserId.isBlank()) {
            return Long.parseLong(xUserId);
        }
        return 1L;
    }

    @GetMapping
    public CartResponse getCart(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        return cartService.getCartByUserId(resolveUserId(xUserId));
    }

    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addToCart(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestBody AddToCartRequest request) {
        return cartService.addToCart(resolveUserId(xUserId), request.getProductId(), request.getQuantity());
    }

    @DeleteMapping("/remove/{cartItemId}")
    public void removeFromCart(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
    }

    @PutMapping(value = "/update/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateCartItem(
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(cartItemId, request.getQuantity());
    }

    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse checkout(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestBody(required = false) CheckoutRequest request) {
        String address = (request != null && request.getShippingAddress() != null)
                ? request.getShippingAddress() : "";
        return cartService.checkout(resolveUserId(xUserId), address);
    }
}
