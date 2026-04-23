package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Одна позиция в заказе.
 *
 * Ключевое отличие от CartItem: здесь хранится priceAtOrder — цена товара
 * на момент оформления заказа. Это «снимок» цены.
 *
 * Зачем? Продавец может изменить цену товара позднее, но стоимость
 * уже созданных заказов должна оставаться прежней.
 *
 * Связи:
 *   ManyToOne с Order   — много позиций принадлежат одному заказу.
 *   ManyToOne с Product — каждая позиция ссылается на товар (для истории).
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Ссылка на товар. При удалении товара эта ссылка станет null (если не настроить cascade),
    // поэтому на production нужно добавить soft-delete или запрет удаления товаров с заказами.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    // Цена за единицу товара на момент оформления заказа — фиксируется в CartService.checkout().
    private BigDecimal priceAtOrder;
}
