package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Запись о попытке оплаты счёта.
 *
 * Создаётся в InvoiceService.pay() при каждой попытке оплаты.
 * Сейчас всегда создаётся со статусом SUCCESS (заглушка).
 * В будущем — здесь будет ответ от платёжного шлюза (Stripe, ЮKassa и т.д.).
 *
 * ManyToOne с Invoice: один счёт может иметь несколько попыток оплаты.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Счёт, к которому относится эта попытка оплаты.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    // Сумма платежа — копируется из Invoice.amount.
    private BigDecimal amount;

    // Способ оплаты: "CARD", "SBP", "CASH" и т.д. (свободная строка, не enum).
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // Точное время попытки оплаты.
    private LocalDateTime timestamp;
}
