package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Счёт на оплату заказа.
 *
 * Паттерн «Invoice»: отделяет факт заказа от факта оплаты.
 * Один Order → один Invoice (OneToOne).
 * Invoice может иметь несколько Payment-ов (попыток оплаты).
 *
 * Создаётся автоматически при оформлении заказа (CartService.checkout).
 * Оплачивается через InvoiceService.pay() → POST /api/invoice/{id}/pay.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true в @JoinColumn — гарантия OneToOne: один заказ, один счёт.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    // Сумма к оплате — копируется из Order.totalAmount при создании счёта.
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    private LocalDateTime createdAt;

    // Заполняется в InvoiceService.pay() в момент успешной оплаты.
    private LocalDateTime paidAt;

    // История попыток оплаты. cascade = ALL: удаление Invoice удалит все Payment-ы.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<Payment> payments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
