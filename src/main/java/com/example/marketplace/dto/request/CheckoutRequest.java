package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Тело запроса POST /api/cart/checkout (оформление заказа).
 *
 * Адрес доставки обязателен — без него заказ создать нельзя.
 * В будущем здесь может появиться promocode, выбор способа доставки и т.д.
 */
@Data
public class CheckoutRequest {

    @NotBlank(message = "Адрес доставки обязателен")
    private String shippingAddress;
}
