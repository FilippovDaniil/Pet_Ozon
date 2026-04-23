package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Тело запроса POST /api/cart/add.
 *
 * @Data (Lombok) — генерирует сразу: @Getter + @Setter + @ToString + @EqualsAndHashCode
 *   + @RequiredArgsConstructor. Удобно для простых DTO.
 *
 * @NotNull — productId не должен быть null (в отличие от @NotBlank, работает с любым типом).
 * @Min(1)  — количество должно быть не менее 1.
 *
 * Пример JSON-запроса:
 *   {"productId": 5, "quantity": 2}
 */
@Data
public class AddToCartRequest {

    @NotNull(message = "ID товара обязателен")
    private Long productId;

    @Min(value = 1, message = "Количество должно быть не менее 1")
    private int quantity;
}
