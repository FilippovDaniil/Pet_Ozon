package com.example.marketplace.repository;

import com.example.marketplace.entity.Cart;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий корзин.
 *
 * У каждого пользователя ровно одна корзина (OneToOne).
 * Метод findByUser ищет корзину по объекту пользователя,
 * что соответствует SQL: SELECT * FROM carts WHERE user_id = ?
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(User user);
}
