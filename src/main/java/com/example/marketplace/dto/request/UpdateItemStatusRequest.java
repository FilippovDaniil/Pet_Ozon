package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Тело запроса на изменение статуса позиции BNPL-заказа.
 * Используется в PATCH /api/orders/{id}/items/{itemId}.
 * Допустимые значения status: ISSUED, CANCELLED, RETURNED.
 * quantity — сколько единиц обработать (по умолчанию 1, если не указано).
 */
public record UpdateItemStatusRequest(

        @NotNull(message = "Укажите статус: ISSUED, CANCELLED или RETURNED")
        String status,

        Integer quantity
) {}
