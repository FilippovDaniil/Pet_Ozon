package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Тело запроса POST /api/products/{id}/reviews.
 *
 * rating: обязательное число от 1 до 5.
 *   @Min(1) — оценка не может быть 0 или отрицательной.
 *   @Max(5) — оценка не может быть выше 5 (шкала «звёздочек»).
 *   @NotNull — без оценки отзыв принять нельзя.
 *
 * comment: необязательный текст. Если не указан — только рейтинг без слов.
 */
@Data
public class CreateReviewRequest {

    @NotNull(message = "Оценка обязательна")
    @Min(value = 1, message = "Минимальная оценка — 1")
    @Max(value = 5, message = "Максимальная оценка — 5")
    private Integer rating;

    private String comment;
}
