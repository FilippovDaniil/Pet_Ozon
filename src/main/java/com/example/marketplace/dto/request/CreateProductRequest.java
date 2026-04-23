package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Тело запроса для создания / обновления товара.
 *
 * Используется в:
 *   POST /api/admin/products      — AdminController.createProduct()
 *   PUT  /api/admin/products/{id} — AdminController.updateProduct()
 *   POST /api/seller/products     — SellerController.createProduct()
 *   PUT  /api/seller/products/{id}— SellerController.updateProduct()
 *
 * @Positive — число должно быть строго больше нуля (цена не может быть 0 или отрицательной).
 * @Min(0)   — количество на складе может быть 0 (товар закончился), но не отрицательным.
 *
 * BigDecimal используется для денег: double теряет точность при арифметике с дробями.
 */
@Data
public class CreateProductRequest {

    @NotBlank(message = "Название товара обязательно")
    private String name;

    private String description;

    @NotNull(message = "Цена обязательна")
    @Positive(message = "Цена должна быть положительной")
    private BigDecimal price;

    @Min(value = 0, message = "Количество на складе не может быть отрицательным")
    private int stockQuantity;

    private String imageUrl;

    private String category;
}
