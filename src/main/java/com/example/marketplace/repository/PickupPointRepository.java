package com.example.marketplace.repository;

import com.example.marketplace.entity.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий точек самовывоза.
 */
public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    // Активные точки для клиента — отсортированы по названию.
    List<PickupPoint> findByActiveTrueOrderByNameAsc();

    // Все точки (для админа) — отсортированы по названию.
    List<PickupPoint> findAllByOrderByNameAsc();
}
