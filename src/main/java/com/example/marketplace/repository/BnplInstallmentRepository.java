package com.example.marketplace.repository;

import com.example.marketplace.entity.BnplContract;
import com.example.marketplace.entity.BnplInstallment;
import com.example.marketplace.entity.enums.BnplInstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BnplInstallmentRepository extends JpaRepository<BnplInstallment, Long> {

    List<BnplInstallment> findByContractOrderByInstallmentNumberAsc(BnplContract contract);

    Optional<BnplInstallment> findByContractAndInstallmentNumber(BnplContract contract, int number);

    // Взносы, которые нужно списать сегодня (для планировщика).
    @Query("SELECT i FROM BnplInstallment i WHERE i.status = 'PENDING' AND i.dueDate <= :today")
    List<BnplInstallment> findDueInstallments(LocalDate today);
}
