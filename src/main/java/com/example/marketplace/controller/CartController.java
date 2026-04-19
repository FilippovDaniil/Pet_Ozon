package com.example.marketplace.controller;

import com.example.marketplace.dto.request.AddToCartRequest;
import com.example.marketplace.dto.request.CheckoutRequest;
import com.example.marketplace.dto.request.UpdateCartItemRequest;
import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal User user) {
        return cartService.getCartByUserId(user.getId());
    }

    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addToCart(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(user.getId(), request.getProductId(), request.getQuantity());
    }

    @DeleteMapping("/remove/{cartItemId}")
    public void removeFromCart(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
    }

    @PutMapping(value = "/update/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateCartItem(@PathVariable Long cartItemId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(cartItemId, request.getQuantity());
    }

    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse checkout(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody CheckoutRequest request) {
        return cartService.checkout(user.getId(), request.getShippingAddress());
    }
}
