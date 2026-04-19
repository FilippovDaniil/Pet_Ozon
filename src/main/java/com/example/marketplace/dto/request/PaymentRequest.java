package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentRequest {
    @NotBlank(message = "Способ оплаты обязателен")
    private String paymentMethod;
}
