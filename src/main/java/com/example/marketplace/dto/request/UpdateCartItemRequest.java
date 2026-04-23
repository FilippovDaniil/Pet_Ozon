package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Тело запроса PUT /api/cart/update/{cartItemId}.
 *
 * Используется для изменения количества конкретной позиции в корзине.
 * Чтобы удалить позицию — используй DELETE /api/cart/remove/{id}.
 */
@Data
public class UpdateCartItemRequest {

    @Min(value = 1, message = "Количество должно быть не менее 1")
    private int quantity;
}
