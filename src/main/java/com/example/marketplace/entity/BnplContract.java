package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.BnplContractStatus;
import com.example.marketplace.entity.enums.BnplProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BNPL-контракт: договорённость о рассрочке по конкретному заказу.
 *
 * Создаётся в момент выбора BNPL-продукта клиентом.
 * Содержит итоговую сумму (с комиссией) и ссылку на график взносов.
 *
 * Жизненный цикл: AWAITING_PAYMENT → ACTIVE → COMPLETED (или CANCELLED).
 */
@Entity
@Table(name = "bnpl_contracts")
@Getter
@Setter
@NoArgsConstructor
public class BnplContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Заказ, к которому привязана рассрочка. Один заказ — один контракт.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true, nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BnplProduct product;

    // Итоговая сумма рассрочки в копейках = сумма заказа + комиссия.
    @Column(nullable = false)
    private Long totalAmountKopecks;

    // Размер комиссии в копейках.
    @Column(nullable = false)
    private Long commissionKopecks;

    // Количество взносов (дублирует product.installmentCount для удобства).
    @Column(nullable = false)
    private Integer installmentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private BnplContractStatus status;

    // orderId pre-auth операции в шлюзе Альфа Банка.
    @Column(length = 64)
    private String alfaPreAuthOrderId;

    // bindingId сохраняется после первого успешного deposit.do.
    // Используется планировщиком для авто-списания следующих взносов.
    @Column(length = 128)
    private String bindingId;

    // Общая уже задепозированная сумма в копейках (обновляется при каждом deposit.do).
    @Column(nullable = false)
    private Long depositedAmountKopecks = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<BnplInstallment> installments = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
