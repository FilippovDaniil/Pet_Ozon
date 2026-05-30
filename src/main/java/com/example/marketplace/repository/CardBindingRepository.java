package com.example.marketplace.repository;

import com.example.marketplace.entity.CardBinding;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий привязанных карт пользователей (bindingId для рекуррентных списаний).
 */
public interface CardBindingRepository extends JpaRepository<CardBinding, Long> {

    // Карты пользователя: сначала дефолтная, затем новые — для списка в UI.
    List<CardBinding> findByUserOrderByIsDefaultDescCreatedAtDesc(User user);

    // Поиск по bindingId банка — для проверки дублей и резолва связки.
    Optional<CardBinding> findByBindingId(String bindingId);

    // Дефолтная карта пользователя — с неё идут авто-списания.
    Optional<CardBinding> findByUserAndIsDefaultTrue(User user);

    // Проверка, что такая привязка уже сохранена (не дублировать).
    boolean existsByBindingId(String bindingId);

    // Снимает флаг default со всех карт пользователя перед установкой новой дефолтной.
    @Modifying
    @Query("UPDATE CardBinding c SET c.isDefault = false WHERE c.user = :user")
    void clearDefaultForUser(User user);
}
