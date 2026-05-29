package com.example.marketplace.repository;

import com.example.marketplace.entity.CardBindRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardBindRequestRepository extends JpaRepository<CardBindRequest, Long> {
    Optional<CardBindRequest> findByAlfaOrderId(String alfaOrderId);
}
