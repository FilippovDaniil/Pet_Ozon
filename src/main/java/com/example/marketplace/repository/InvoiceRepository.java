package com.example.marketplace.repository;

import com.example.marketplace.entity.Invoice;
import com.example.marketplace.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByOrder(Order order);
}
