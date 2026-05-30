package com.example.marketplace.repository;

import com.example.marketplace.entity.AlfaBankOrder;
import com.example.marketplace.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий платёжных операций в шлюзе Альфа Банка.
 * Связывает наш внутренний orderNumber и UUID банка (alfaOrderId) со счётом.
 */
public interface AlfaBankOrderRepository extends JpaRepository<AlfaBankOrder, Long> {

    // Поиск по нашему внутреннему номеру заказа (PG-UUID16).
    Optional<AlfaBankOrder> findByOrderNumber(String orderNumber);

    // Поиск по UUID, который вернул банк — нужен в callback после redirect.
    Optional<AlfaBankOrder> findByAlfaOrderId(String alfaOrderId);

    // Платёжная операция, привязанная к конкретному счёту.
    Optional<AlfaBankOrder> findByInvoice(Invoice invoice);
}
