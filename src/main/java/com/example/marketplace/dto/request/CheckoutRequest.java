package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Адрес доставки обязателен")
    private String shippingAddress;
}
