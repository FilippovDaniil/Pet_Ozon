package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.BnplInstallmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Один взнос в графике BNPL-рассрочки.
 *
 * Первый взнос (installmentNumber = 1) депозируется при выдаче первого товара.
 * Последующие взносы списываются автоматически планировщиком в день dueDate.
 */
@Entity
@Table(name = "bnpl_installments")
@Getter
@Setter
@NoArgsConstructor
public class BnplInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private BnplContract contract;

    // Порядковый номер взноса (1 — первый, N — последний).
    @Column(nullable = false)
    private Integer installmentNumber;

    // Сумма взноса в копейках.
    @Column(nullable = false)
    private Long amountKopecks;

    // Дата, в которую должен быть произведён платёж.
    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private BnplInstallmentStatus status;

    // Момент фактического списания.
    private LocalDateTime paidAt;

    // orderId из шлюза для этого конкретного взноса (заполняется после оплаты).
    @Column(length = 64)
    private String alfaOrderId;

    // Суммарное количество дней, на которые уже был перенесён этот взнос.
    // Максимум 14. Каждый отдельный перенос — не менее 3 дней.
    // columnDefinition нужен чтобы ddl-auto=update смог добавить колонку в таблицу с уже существующими строками.
    @Column(columnDefinition = "integer default 0")
    private Integer daysPostponed = 0;

    // Накопленная комиссия за все переносы (в копейках), уже включена в amountKopecks.
    @Column(columnDefinition = "bigint default 0")
    private Long postponeFeePaidKopecks = 0L;
}
