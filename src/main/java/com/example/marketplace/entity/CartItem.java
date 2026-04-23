package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Одна позиция в корзине: конкретный товар + количество.
 *
 * CartItem — «дочерняя» сущность корзины.
 * Цена не хранится здесь специально: при отображении корзины она берётся из Product.price.
 * (Для заказа цена фиксируется в OrderItem.priceAtOrder — чтобы изменение цены товара
 * не меняло стоимость уже созданных заказов.)
 *
 * Связи:
 *   ManyToOne с Cart    — много позиций принадлежит одной корзине.
 *   ManyToOne с Product — каждая позиция ссылается на один товар.
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @JoinColumn создаёт колонку cart_id в таблице cart_items (внешний ключ).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    // Ссылка на товар. Цену берём из product.getPrice() при необходимости.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Количество единиц товара в корзине. Всегда >= 1.
    private int quantity;
}
