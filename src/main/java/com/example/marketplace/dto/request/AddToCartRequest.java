package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddToCartRequest {

    @NotNull(message = "ID товара обязателен")
    private Long productId;

    @Min(value = 1, message = "Количество должно быть не менее 1")
    private int quantity;
}
