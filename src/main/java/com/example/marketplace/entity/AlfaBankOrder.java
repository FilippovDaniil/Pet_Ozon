package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.AlfaBankOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Запись о платёжной операции в шлюзе Альфа Банка.
 *
 * Используется как для одностадийной полной оплаты (FULL),
 * так и для BNPL-операций (pre-auth, installment).
 *
 * Один Invoice или один BnplInstallment ←→ один AlfaBankOrder.
 */
@Entity
@Table(name = "alfa_bank_orders")
@Getter
@Setter
@NoArgsConstructor
public class AlfaBankOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Внутренний уникальный номер заказа — передаётся в orderNumber шлюза.
    @Column(nullable = false, unique = true, length = 64)
    private String orderNumber;

    // orderId, возвращённый шлюзом после register.do / registerPreAuth.do.
    @Column(length = 64)
    private String alfaOrderId;

    // Ссылка на Invoice — заполняется для полной оплаты.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    // Ссылка на взнос BNPL — заполняется для installment-платежей.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bnpl_installment_id")
    private BnplInstallment bnplInstallment;

    // Ссылка на BNPL-контракт — заполняется для pre-auth операции.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bnpl_contract_id")
    private BnplContract bnplContract;

    // Сумма в копейках (рубли × 100).
    @Column(nullable = false)
    private Long amountKopecks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlfaBankOrderStatus status;

    // URL формы банка для редиректа клиента.
    @Column(length = 512)
    private String formUrl;

    // bindingId сохраняется после первого deposit.do — используется для авто-списания следующих взносов.
    @Column(length = 128)
    private String bindingId;

    // Для переноса взноса через форму банка (PSTP-заказы): на сколько дней переносим.
    // Применяется в confirmPostponeForm после успешной оплаты комиссии. Null для прочих операций.
    @Column
    private Integer postponeDays;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
