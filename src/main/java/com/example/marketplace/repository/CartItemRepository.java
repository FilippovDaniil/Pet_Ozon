package com.example.marketplace.repository;

import com.example.marketplace.entity.Cart;
import com.example.marketplace.entity.CartItem;
import com.example.marketplace.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий позиций корзины.
 *
 * Метод findByCartAndProduct нужен в CartService.addToCart():
 * когда пользователь добавляет товар, который уже есть в корзине,
 * мы не создаём новую запись, а увеличиваем quantity у существующей.
 *
 * SQL: SELECT * FROM cart_items WHERE cart_id = ? AND product_id = ?
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
}
