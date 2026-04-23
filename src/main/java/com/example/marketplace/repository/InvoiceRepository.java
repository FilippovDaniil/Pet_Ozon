package com.example.marketplace.repository;

import com.example.marketplace.entity.Invoice;
import com.example.marketplace.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий счетов на оплату.
 *
 * findByOrder используется в:
 *   InvoiceService — при получении счёта по заказу.
 *   OrderService.toResponse() — чтобы добавить invoiceId в ответ о заказе.
 *
 * SQL: SELECT * FROM invoices WHERE order_id = ?
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrder(Order order);
}
