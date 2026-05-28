package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на перенос взноса.
 * days — количество дней переноса (от 3 до 14 за один раз).
 */
public record PostponeInstallmentRequest(
        @NotNull
        @Min(value = 3, message = "Минимальный перенос — 3 дня")
        @Max(value = 14, message = "Максимальный перенос за раз — 14 дней")
        Integer days
) {}
