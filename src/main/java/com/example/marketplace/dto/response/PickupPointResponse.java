package com.example.marketplace.dto.response;

/**
 * Точка самовывоза для UI (клиент и админ).
 */
public record PickupPointResponse(
        Long id,
        String name,
        String address,
        String metro,
        boolean active
) {}
