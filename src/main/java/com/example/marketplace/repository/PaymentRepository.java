package com.example.marketplace.repository;

import com.example.marketplace.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий платёжных транзакций.
 *
 * Пока используется только для сохранения (save) записи о платеже
 * в InvoiceService.pay(). Дополнительные методы будут добавлены,
 * когда появится история платежей пользователя или возвраты.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
