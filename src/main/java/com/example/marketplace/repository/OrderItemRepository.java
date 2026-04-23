package com.example.marketplace.repository;

import com.example.marketplace.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий позиций заказа.
 *
 * Здесь нет дополнительных методов: позиции заказа создаются через
 * orderItemRepository.save() в CartService.checkout() и загружаются
 * каскадно вместе с Order (OneToMany с cascade = ALL).
 * Прямые запросы к OrderItem извне пока не нужны.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
