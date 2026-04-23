package com.example.marketplace.repository;

import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Репозиторий заказов.
 *
 * findByUser — стандартный запрос по имени метода (SQL: WHERE user_id = ?).
 * Перегружен дважды: со списком и с пагинацией — Spring сам разберётся.
 *
 * findBySellerId — кастомный JPQL-запрос (@Query).
 * JPQL работает с классами и полями Java, а не с таблицами и колонками БД.
 * Запрос: «найди все уникальные заказы, в которых хотя бы одна позиция принадлежит этому продавцу».
 * SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId
 * JPA сам переведёт это в нужный SQL с JOIN.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    // Тот же запрос, но с пагинацией — используется в OrderService.getOrdersByUserId().
    Page<Order> findByUser(User user, Pageable pageable);

    // @Query + @Param: находит заказы, содержащие товары данного продавца.
    @Query("SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    List<Order> findBySellerId(@Param("sellerId") Long sellerId);
}
