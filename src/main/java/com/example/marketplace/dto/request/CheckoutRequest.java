package com.example.marketplace.dto.request;

import com.example.marketplace.entity.enums.DeliveryType;
import lombok.Data;

/**
 * Тело запроса POST /api/orders (оформление заказа).
 *
 * Способ получения:
 *   • DELIVERY (по умолчанию) — нужен {@code shippingAddress} (адрес доставки).
 *   • PICKUP                  — нужен {@code pickupPointId} (точка самовывоза).
 *
 * Условную валидацию выполняет CartService.checkout (зависит от deliveryType),
 * поэтому @NotBlank на адрес здесь не ставим.
 */
@Data
public class CheckoutRequest {

    // null трактуется как DELIVERY (обратная совместимость со старым клиентом).
    private DeliveryType deliveryType;

    // Адрес доставки — обязателен для DELIVERY.
    private String shippingAddress;

    // Id точки самовывоза — обязателен для PICKUP.
    private Long pickupPointId;
}
