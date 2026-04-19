package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateCartItemRequest {

    @Min(value = 1, message = "Количество должно быть не менее 1")
    private int quantity;
}
