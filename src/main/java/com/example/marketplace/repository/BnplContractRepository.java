package com.example.marketplace.repository;

import com.example.marketplace.entity.BnplContract;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.BnplContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BnplContractRepository extends JpaRepository<BnplContract, Long> {

    Optional<BnplContract> findByOrder(Order order);

    Optional<BnplContract> findByAlfaPreAuthOrderId(String alfaPreAuthOrderId);

    // Все контракты клиента для отображения в личном кабинете.
    @Query("SELECT c FROM BnplContract c WHERE c.order.user = :user ORDER BY c.createdAt DESC")
    List<BnplContract> findByUser(User user);

    // Активные контракты для планировщика авто-списания.
    List<BnplContract> findByStatus(BnplContractStatus status);
}
