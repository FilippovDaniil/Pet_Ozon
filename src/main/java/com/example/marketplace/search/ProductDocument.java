package com.example.marketplace.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Документ OpenSearch — «плоская» проекция товара для полнотекстового поиска.
// Хранится в индексе "products". Не связан с JPA: это не @Entity.
// @Data — Lombok: геттеры, сеттеры, equals, hashCode, toString.
// @NoArgsConstructor нужен Jackson для десериализации ответов OpenSearch.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    // id хранится как строка (OpenSearch document _id всегда строка)
    private String id;

    // Поля для полнотекстового поиска (text в OpenSearch — анализируется)
    private String name;
    private String description;

    // Числовые поля для фильтрации по цене
    private Double price;

    // Keyword-поля — точное совпадение (term query), не анализируются
    private String category;
    private String shopName;

    private Integer stockQuantity;
}
