package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.DeliveryType;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.PaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Заказ покупателя.
 *
 * Создаётся из корзины при вызове CartService.checkout().
 * Содержит «снимок» корзины: список OrderItem-ов с зафиксированными ценами.
 * К заказу автоматически создаётся Invoice (счёт на оплату).
 *
 * Жизненный цикл: CREATED → PAID → DELIVERED (или CANCELLED).
 *
 * Связи:
 *   ManyToOne с User      — один пользователь может иметь много заказов.
 *   OneToMany с OrderItem — один заказ содержит много позиций.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Покупатель, оформивший заказ.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Момент оформления заказа — фиксируется в CartService.checkout().
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // Тип оплаты: FULL (одностадийный) или один из BNPL-продуктов.
    // null — до момента выбора способа оплаты.
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    // Итоговая сумма = сумма всех (quantity × priceAtOrder) позиций.
    private BigDecimal totalAmount;

    // Способ получения: DELIVERY (доставка) или PICKUP (самовывоз).
    // Колонка nullable — у заказов, созданных до появления фичи, значение остаётся null.
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DeliveryType deliveryType;

    // Адрес получения: для DELIVERY — введённый клиентом адрес;
    // для PICKUP — текстовый снимок адреса точки самовывоза (название + адрес + метро).
    private String shippingAddress;

    // cascade = ALL, orphanRemoval = true: удаление Order удаляет все его OrderItem-ы.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
