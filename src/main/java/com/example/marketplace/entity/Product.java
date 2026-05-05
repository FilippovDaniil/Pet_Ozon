package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Товар в каталоге маркетплейса.
 *
 * Каждый товар принадлежит одному продавцу (seller).
 * Связь ManyToOne: много товаров → один продавец.
 *
 * stockQuantity уменьшается при оформлении заказа (CartService.checkout).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // Остаток на складе. Уменьшается при оформлении заказа.
    private int stockQuantity;

    // URL изображения товара (необязательное поле).
    private String imageUrl;

    // Изображение товара в кодировке Base64, хранится прямо в БД.
    // @Column(columnDefinition = "TEXT") — явно указываем PostgreSQL-тип TEXT.
    // По умолчанию JPA использует VARCHAR(255), которого не хватит для Base64 изображения.
    // TEXT в PostgreSQL: до 1 ГБ, без дополнительной структуры хранения.
    @Column(columnDefinition = "TEXT")
    private String imageData;

    // MIME-тип загруженного файла: "image/jpeg", "image/png", "image/webp" и т.д.
    // Нужен клиенту, чтобы собрать data-URL: data:<contentType>;base64,<imageData>
    private String imageContentType;

    // Категория: "Ноутбуки", "Периферия", "Аудио" и т.д.
    private String category;

    // FetchType.LAZY — данные продавца НЕ загружаются из БД до первого обращения к ним.
    // Это оптимизация: если продавец не нужен — лишнего JOIN не будет.
    // @JoinColumn — создаёт колонку seller_id (внешний ключ) в таблице products.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
