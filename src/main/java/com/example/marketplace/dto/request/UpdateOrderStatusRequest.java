package com.example.marketplace.dto.request;

import com.example.marketplace.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Тело запроса PUT /api/admin/orders/{id}/status.
 *
 * Jackson автоматически десериализует строку "PAID" → enum OrderStatus.PAID.
 * Если придёт неизвестное значение (например, "UNKNOWN") — Jackson выбросит
 * HttpMessageNotReadableException → GlobalExceptionHandler вернёт 400.
 */
@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "Статус заказа обязателен")
    private OrderStatus status;
}
