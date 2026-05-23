package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Категория товаров.
 *
 * Вместо строки в поле Product.category теперь используется ссылка на эту сущность.
 * Это даёт: целостность данных (нельзя написать произвольную строку), единый список
 * категорий через GET /api/categories, возможность добавлять поля (иконка, slug и т.д.).
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Уникальное название категории: "Ноутбуки", "Аудио" и т.д.
    @Column(nullable = false, unique = true)
    private String name;

    public Category(String name) {
        this.name = name;
    }
}
