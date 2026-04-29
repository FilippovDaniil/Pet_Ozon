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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты для InvoiceService — проверяем логику оплаты и начисления баланса продавцу.
// MockitoExtension заменяет реальные репозитории на моки, БД не используется.
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    // Моки всех репозиториев, которые использует InvoiceService
    @Mock InvoiceRepository invoiceRepository;
    @Mock OrderRepository   orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository    userRepository;

    // InvoiceService создаётся с подставленными выше моками вместо реальных зависимостей
    @InjectMocks
    InvoiceService invoiceService;

    // Вспомогательный метод: создаёт Invoice с заданными параметрами.
    // Счёт всегда связан с заказом (Order) — поэтому создаём оба объекта.
    private Invoice makeInvoice(Long id, BigDecimal amount, InvoiceStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        order.setItems(new ArrayList<>());

        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setOrder(order);
        invoice.setAmount(amount);
        invoice.setStatus(status);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setPayments(new ArrayList<>());
        return invoice;
    }

    // Вспомогательный метод: настраивает мок paymentRepository так, чтобы при сохранении
    // любого Payment возвращался готовый объект с нужными данными.
    private Payment stubPaymentSave(Invoice invoice, String method) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getAmount());
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTimestamp(LocalDateTime.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        return payment;
    }

    // ── getAllInvoices ────────────────────────────────────────────────────────

    @Test
    void getAllInvoices_returnsPage() {
        // getAllInvoices теперь принимает Pageable и возвращает Page<InvoiceResponse>.
        // PageImpl — реализация Page для тестов: содержит список, pageable и totalElements.
        PageImpl<Invoice> invoicePage = new PageImpl<>(List.of(
                makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.UNPAID),
                makeInvoice(2L, new BigDecimal("3000.00"), InvoiceStatus.PAID)
        ));
        // findAll(Pageable) — перегрузка JpaRepository, которая поддерживает пагинацию.
        // any(Pageable.class) матчит любую реализацию Pageable (PageRequest, SliceRequest и др.)
        when(invoiceRepository.findAll(any(Pageable.class))).thenReturn(invoicePage);

        Page<InvoiceResponse> result = invoiceService.getAllInvoices(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ── getInvoiceById ────────────────────────────────────────────────────────

    @Test
    void getInvoiceById_found_returnsResponse() {
        Invoice invoice = makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        InvoiceResponse result = invoiceService.getInvoiceById(1L);

        // Проверяем все ключевые поля DTO-ответа
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(result.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.getOrderId()).isEqualTo(1L); // id связанного заказа
    }

    @Test
    void getInvoiceById_notFound_throwsException() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoiceById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99"); // сообщение об ошибке должно содержать id
    }

    // ── pay ───────────────────────────────────────────────────────────────────

    @Test
    void pay_unpaidInvoice_marksAsPaidAndCreatesPayment() {
        Invoice invoice = makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        stubPaymentSave(invoice, "CARD");

        PaymentResponse result = invoiceService.pay(1L, "CARD");

        // Проверяем поля ответа
        assertThat(result.getPaymentMethod()).isEqualTo("CARD");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.getInvoiceId()).isEqualTo(1L);

        // Проверяем что статусы обновились в объектах
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull(); // дата оплаты должна быть установлена
        assertThat(invoice.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);

        // Проверяем что все три сохранения действительно произошли
        verify(invoiceRepository).save(invoice);
        verify(orderRepository).save(invoice.getOrder());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void pay_withCashMethod_recordsCashPayment() {
        Invoice invoice = makeInvoice(1L, new BigDecimal("2000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        stubPaymentSave(invoice, "CASH");

        PaymentResponse result = invoiceService.pay(1L, "CASH");

        assertThat(result.getPaymentMethod()).isEqualTo("CASH");
    }

    @Test
    void pay_nullMethod_defaultsToCard() {
        // Если метод оплаты не передан (null), сервис должен использовать "CARD" по умолчанию
        Invoice invoice = makeInvoice(1L, new BigDecimal("1000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        stubPaymentSave(invoice, "CARD");

        PaymentResponse result = invoiceService.pay(1L, null);

        assertThat(result.getPaymentMethod()).isEqualTo("CARD");
    }

    @Test
    void pay_blankMethod_defaultsToCard() {
        // Пустая строка (только пробелы) тоже должна заменяться на "CARD"
        Invoice invoice = makeInvoice(1L, new BigDecimal("1000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        stubPaymentSave(invoice, "CARD");

        PaymentResponse result = invoiceService.pay(1L, "   ");

        assertThat(result.getPaymentMethod()).isEqualTo("CARD");
    }

    @Test
    void pay_alreadyPaid_throwsIllegalArgument() {
        // Защита от двойной оплаты: если счёт уже PAID — бросить исключение
        Invoice invoice = makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.PAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.pay(1L, "CARD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already paid");

        // never() — убедиться что save на paymentRepository НЕ вызывался (платёж не создан)
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_invoiceNotFound_throwsException() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.pay(99L, "CARD"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── seller balance update (new functionality) ──────────────────────────────
    // Тесты для начисления выручки продавцу при оплате заказа.

    @Test
    void pay_withSellerItems_updatesSellerBalance() {
        // Создаём продавца с начальным балансом 1000
        User seller = new User();
        seller.setId(10L);
        seller.setBalance(new BigDecimal("1000.00"));

        // Товар принадлежит этому продавцу
        Product product = new Product();
        product.setId(1L);
        product.setSeller(seller);

        // Позиция заказа: 2 штуки по 500 рублей
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPriceAtOrder(new BigDecimal("500.00"));

        Invoice invoice = makeInvoice(1L, new BigDecimal("1000.00"), InvoiceStatus.UNPAID);
        invoice.getOrder().setItems(new ArrayList<>(List.of(item)));

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        when(userRepository.save(seller)).thenReturn(seller);
        stubPaymentSave(invoice, "CARD");

        invoiceService.pay(1L, "CARD");

        // seller.balance = 1000 + (500 * 2) = 2000
        assertThat(seller.getBalance()).isEqualByComparingTo("2000.00");
        verify(userRepository).save(seller);
    }

    @Test
    void pay_withMultipleSellers_updatesEachBalance() {
        // Два продавца с разными начальными балансами
        User seller1 = new User(); seller1.setId(10L); seller1.setBalance(BigDecimal.ZERO);
        User seller2 = new User(); seller2.setId(11L); seller2.setBalance(new BigDecimal("500.00"));

        Product p1 = new Product(); p1.setId(1L); p1.setSeller(seller1);
        Product p2 = new Product(); p2.setId(2L); p2.setSeller(seller2);

        // seller1 продаёт 1 товар за 300 → заработок 300
        OrderItem item1 = new OrderItem();
        item1.setProduct(p1); item1.setQuantity(1); item1.setPriceAtOrder(new BigDecimal("300.00"));

        // seller2 продаёт 3 товара за 200 → заработок 600
        OrderItem item2 = new OrderItem();
        item2.setProduct(p2); item2.setQuantity(3); item2.setPriceAtOrder(new BigDecimal("200.00"));

        Invoice invoice = makeInvoice(1L, new BigDecimal("900.00"), InvoiceStatus.UNPAID);
        invoice.getOrder().setItems(new ArrayList<>(List.of(item1, item2)));

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        // thenAnswer возвращает тот же объект, что был передан в save — имитирует реальный репозиторий
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubPaymentSave(invoice, "CARD");

        invoiceService.pay(1L, "CARD");

        // seller1: 0 + 300 = 300
        assertThat(seller1.getBalance()).isEqualByComparingTo("300.00");
        // seller2: 500 + 600 = 1100
        assertThat(seller2.getBalance()).isEqualByComparingTo("1100.00");
        // times(2) — убедиться что save был вызван ровно два раза (по одному на каждого продавца)
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void pay_withProductWithoutSeller_doesNotUpdateBalance() {
        // Товар без продавца (например, создан администратором) — баланс никому не начисляется
        Product product = new Product();
        product.setId(1L);
        product.setSeller(null); // seller = null

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrder(new BigDecimal("500.00"));

        Invoice invoice = makeInvoice(1L, new BigDecimal("500.00"), InvoiceStatus.UNPAID);
        invoice.getOrder().setItems(new ArrayList<>(List.of(item)));

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        stubPaymentSave(invoice, "CARD");

        invoiceService.pay(1L, "CARD");

        // never() — убедиться что userRepository.save вообще не вызывался
        verify(userRepository, never()).save(any());
    }
}
