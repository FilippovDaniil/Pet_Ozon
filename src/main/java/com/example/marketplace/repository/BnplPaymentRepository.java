package com.example.marketplace.repository;

import com.example.marketplace.entity.BnplContract;
import com.example.marketplace.entity.BnplPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий журнала BNPL-платежей.
 */
public interface BnplPaymentRepository extends JpaRepository<BnplPayment, Long> {

    // Все платежи контракта в хронологическом порядке.
    List<BnplPayment> findByContractOrderByPaidAtAsc(BnplContract contract);
}
