package com.example.marketplace.repository;

import com.example.marketplace.entity.CardBindRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий staging-запросов на привязку карты.
 * Запись живёт от старта привязки (PENDING) до callback банка (COMPLETED/FAILED).
 */
public interface CardBindRequestRepository extends JpaRepository<CardBindRequest, Long> {
    // Поиск незавершённого запроса по UUID банка — вызывается в card-bind-callback.
    Optional<CardBindRequest> findByAlfaOrderId(String alfaOrderId);
}
