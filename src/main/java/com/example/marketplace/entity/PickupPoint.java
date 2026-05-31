package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Точка самовывоза (магазин), куда клиент может прийти за заказом.
 *
 * Справочник управляется администратором (CRUD через /api/admin/pickup-points).
 * Клиент видит только активные точки при оформлении заказа.
 *
 * Заказ НЕ хранит FK на точку — вместо этого в Order.shippingAddress пишется
 * текстовый снимок адреса точки. Так удаление/изменение точки не ломает историю заказов.
 */
@Entity
@Table(name = "pickup_points")
@Getter
@Setter
@NoArgsConstructor
public class PickupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Название магазина/ТЦ (например, «ТЦ Афимолл Сити»).
    @Column(nullable = false, length = 128)
    private String name;

    // Адрес точки (улица, дом).
    @Column(nullable = false, length = 256)
    private String address;

    // Ближайшее метро (опционально, для удобства клиента).
    @Column(length = 64)
    private String metro;

    // Активна ли точка. Неактивные не показываются клиенту, но остаются в БД.
    @Column(nullable = false)
    private boolean active = true;
}
