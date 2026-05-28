package com.example.marketplace.repository;

import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CardBindingRepository extends JpaRepository<CardBinding, Long> {

    List<CardBinding> findByUserOrderByIsDefaultDescCreatedAtDesc(User user);

    Optional<CardBinding> findByBindingId(String bindingId);

    Optional<CardBinding> findByUserAndIsDefaultTrue(User user);

    boolean existsByBindingId(String bindingId);

    // Снимает флаг default со всех карт пользователя перед установкой новой дефолтной.
    @Modifying
    @Query("UPDATE CardBinding c SET c.isDefault = false WHERE c.user = :user")
    void clearDefaultForUser(User user);
}
