package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Фактический платёж по BNPL-контракту (запись в журнале транзакций).
 *
 * Один платёж = одно реальное списание денег:
 *   • первый взнос при оформлении рассрочки,
 *   • авто-списание взноса планировщиком,
 *   • произвольный/досрочный платёж клиента.
 *
 * В отличие от BnplInstallment (это строка ГРАФИКА), BnplPayment — это
 * ДЕНЕЖНАЯ ОПЕРАЦИЯ. Один платёж может покрыть несколько взносов или часть взноса,
 * поэтому суммы платежей и суммы взносов разделены.
 */
@Entity
@Table(name = "bnpl_payments")
@Getter
@Setter
@NoArgsConstructor
public class BnplPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Контракт, к которому относится платёж.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private BnplContract contract;

    // Списанная сумма в копейках.
    @Column(nullable = false)
    private Long amountKopecks;

    // Способ/тип платежа: FIRST (первый взнос), SCHEDULED (авто-списание), MANUAL (произвольный).
    @Column(nullable = false, length = 20)
    private String method;

    // Человекочитаемое описание: «Первый взнос», «Авто-списание взноса №3», «Произвольный платёж».
    @Column(length = 128)
    private String description;

    // orderId операции в шлюзе Альфа Банка.
    @Column(length = 64)
    private String alfaOrderId;

    // Момент списания.
    @Column(nullable = false)
    private LocalDateTime paidAt;

    @PrePersist
    private void prePersist() {
        if (paidAt == null) {
            paidAt = LocalDateTime.now();
        }
    }
}
