package com.example.marketplace.service;

import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentResponse;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.PaymentStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.InvoiceRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.PaymentRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сервис оплаты счетов.
 *
 * Главный метод pay() выполняет сразу несколько действий в одной транзакции:
 *   1. Помечает Invoice как оплаченный.
 *   2. Переводит Order в статус PAID.
 *   3. Начисляет деньги продавцам (balance += сумма их товаров).
 *   4. Создаёт запись Payment.
 *
 * Почему всё в одной транзакции?
 * Если баланс продавца обновится, но Payment не запишется (например, ошибка БД),
 * откат транзакции гарантирует консистентность данных.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    /**
     * Возвращает все счета постранично.
     *
     * Page<T> — объект-обёртка: содержит текущую страницу, общее число записей,
     * количество страниц. Клиент получает только нужный «срез» данных,
     * а не весь список, который может быть очень большим.
     *
     * .map(this::toResponse) — трансформирует каждый Invoice в InvoiceResponse
     * прямо внутри страницы, не распаковывая её в список.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<InvoiceResponse> getAllInvoices(Pageable pageable) {
        return invoiceRepository.findAll(pageable).map(this::toResponse);
    }

    public InvoiceResponse getInvoiceById(Long id) {
        return toResponse(findEntityById(id));
    }

    /**
     * Оплата счёта.
     * Идемпотентная проверка: если счёт уже оплачен — бросаем исключение.
     * Это защита от случайного двойного списания.
     */
    @Transactional
    public PaymentResponse pay(Long invoiceId, String paymentMethod) {
        Invoice invoice = findEntityById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("Invoice #" + invoiceId + " is already paid");
        }

        // Шаг 1: обновляем счёт.
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        // Шаг 2: обновляем статус заказа.
        Order order = invoice.getOrder();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // Шаг 3: начисляем деньги продавцам.
        // Если в заказе товары разных продавцов — каждый получает свою долю.
        for (OrderItem item : order.getItems()) {
            User seller = item.getProduct().getSeller();
            if (seller != null) {
                BigDecimal earnings = item.getPriceAtOrder()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal current = seller.getBalance() != null ? seller.getBalance() : BigDecimal.ZERO;
                seller.setBalance(current.add(earnings));
                userRepository.save(seller);
            }
        }

        // Шаг 4: создаём платёжный документ.
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getAmount());
        // Если метод оплаты не указан — по умолчанию "CARD".
        payment.setPaymentMethod(paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "CARD");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTimestamp(LocalDateTime.now());
        paymentRepository.save(payment);

        PaymentResponse r = new PaymentResponse();
        r.setId(payment.getId());
        r.setInvoiceId(invoice.getId());
        r.setAmount(payment.getAmount());
        r.setPaymentMethod(payment.getPaymentMethod());
        r.setStatus(payment.getStatus());
        r.setTimestamp(payment.getTimestamp());
        return r;
    }

    public Invoice findEntityById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(invoice.getId());
        r.setOrderId(invoice.getOrder().getId());
        r.setAmount(invoice.getAmount());
        r.setStatus(invoice.getStatus());
        r.setCreatedAt(invoice.getCreatedAt());
        r.setPaidAt(invoice.getPaidAt());
        return r;
    }
}
