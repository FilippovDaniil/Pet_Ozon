package com.example.marketplace.dto.request;

import com.example.marketplace.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "Статус заказа обязателен")
    private OrderStatus status;
}
