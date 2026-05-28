package com.example.marketplace.repository;

import com.example.marketplace.entity.AlfaBankOrder;
import com.example.marketplace.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlfaBankOrderRepository extends JpaRepository<AlfaBankOrder, Long> {

    Optional<AlfaBankOrder> findByOrderNumber(String orderNumber);

    Optional<AlfaBankOrder> findByAlfaOrderId(String alfaOrderId);

    Optional<AlfaBankOrder> findByInvoice(Invoice invoice);
}
