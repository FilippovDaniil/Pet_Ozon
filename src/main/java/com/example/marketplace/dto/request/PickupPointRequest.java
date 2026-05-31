package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Тело запроса создания/обновления точки самовывоза (админ).
 */
@Data
public class PickupPointRequest {

    @NotBlank(message = "Название точки обязательно")
    private String name;

    @NotBlank(message = "Адрес точки обязателен")
    private String address;

    // Метро — опционально.
    private String metro;

    // Активность. null при создании трактуется как true.
    private Boolean active;
}
