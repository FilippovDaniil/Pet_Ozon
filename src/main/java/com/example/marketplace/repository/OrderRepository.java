package com.example.marketplace.repository;

import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(User user);
    Page<Order> findByUser(User user, Pageable pageable);

    @Query("SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    List<Order> findBySellerId(@Param("sellerId") Long sellerId);
}
