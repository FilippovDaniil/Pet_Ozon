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

/**
 * Контроллер корзины.
 *
 * Все эндпоинты требуют аутентификации (JWT-токена в заголовке Authorization).
 *
 * @AuthenticationPrincipal User user — Spring Security вытаскивает текущего
 * пользователя из SecurityContext (туда его поместил JwtAuthenticationFilter).
 * Это безопаснее, чем передавать userId в параметре — пользователь не может
 * подделать чужой id.
 *
 * Эндпоинты:
 *   GET    /api/cart                    — просмотр корзины
 *   POST   /api/cart/add                — добавить товар
 *   DELETE /api/cart/remove/{id}        — удалить позицию
 *   PUT    /api/cart/update/{id}        — изменить количество
 *   POST   /api/cart/checkout           — оформить заказ
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

    // consumes = JSON: Spring откажет с 415 Unsupported Media Type, если клиент отправит не JSON.
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addToCart(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(user.getId(), request.getProductId(), request.getQuantity());
    }

    // @DeleteMapping: стандартный HTTP DELETE для удаления ресурса.
    @DeleteMapping("/remove/{cartItemId}")
    public void removeFromCart(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
    }

    // @PutMapping: полная замена ресурса (здесь — обновление количества).
    @PutMapping(value = "/update/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateCartItem(@PathVariable Long cartItemId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(cartItemId, request.getQuantity());
    }

    /**
     * POST /api/cart/checkout — оформление заказа.
     * Превращает корзину в Order + Invoice. Корзина после этого очищается.
     * Возвращает созданный OrderResponse.
     */
    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse checkout(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody CheckoutRequest request) {
        return cartService.checkout(user.getId(), request.getShippingAddress());
    }
}
