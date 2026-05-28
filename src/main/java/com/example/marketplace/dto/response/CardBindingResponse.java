package com.example.marketplace.dto.response;

public record CardBindingResponse(
        Long    id,
        String  maskedPan,
        String  expiry,         // MM/YYYY
        boolean isDefault,
        String  createdAt
) {}
