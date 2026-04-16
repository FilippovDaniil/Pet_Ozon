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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock OrderRepository   orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository    userRepository;

    @InjectMocks
    InvoiceService invoiceService;

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
    void getAllInvoices_returnsAll() {
        when(invoiceRepository.findAll()).thenReturn(List.of(
                makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.UNPAID),
                makeInvoice(2L, new BigDecimal("3000.00"), InvoiceStatus.PAID)
        ));

        List<InvoiceResponse> result = invoiceService.getAllInvoices();

        assertThat(result).hasSize(2);
    }

    // ── getInvoiceById ────────────────────────────────────────────────────────

    @Test
    void getInvoiceById_found_returnsResponse() {
        Invoice invoice = makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.UNPAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        InvoiceResponse result = invoiceService.getInvoiceById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(result.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    @Test
    void getInvoiceById_notFound_throwsException() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoiceById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
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

        assertThat(result.getPaymentMethod()).isEqualTo("CARD");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.getInvoiceId()).isEqualTo(1L);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        assertThat(invoice.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);

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
        Invoice invoice = makeInvoice(1L, new BigDecimal("5000.00"), InvoiceStatus.PAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.pay(1L, "CARD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already paid");

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

    @Test
    void pay_withSellerItems_updatesSellerBalance() {
        User seller = new User();
        seller.setId(10L);
        seller.setBalance(new BigDecimal("1000.00"));

        Product product = new Product();
        product.setId(1L);
        product.setSeller(seller);

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
        User seller1 = new User(); seller1.setId(10L); seller1.setBalance(BigDecimal.ZERO);
        User seller2 = new User(); seller2.setId(11L); seller2.setBalance(new BigDecimal("500.00"));

        Product p1 = new Product(); p1.setId(1L); p1.setSeller(seller1);
        Product p2 = new Product(); p2.setId(2L); p2.setSeller(seller2);

        OrderItem item1 = new OrderItem();
        item1.setProduct(p1); item1.setQuantity(1); item1.setPriceAtOrder(new BigDecimal("300.00"));

        OrderItem item2 = new OrderItem();
        item2.setProduct(p2); item2.setQuantity(3); item2.setPriceAtOrder(new BigDecimal("200.00"));

        Invoice invoice = makeInvoice(1L, new BigDecimal("900.00"), InvoiceStatus.UNPAID);
        invoice.getOrder().setItems(new ArrayList<>(List.of(item1, item2)));

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(orderRepository.save(any(Order.class))).thenReturn(invoice.getOrder());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubPaymentSave(invoice, "CARD");

        invoiceService.pay(1L, "CARD");

        assertThat(seller1.getBalance()).isEqualByComparingTo("300.00");
        assertThat(seller2.getBalance()).isEqualByComparingTo("1100.00");
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void pay_withProductWithoutSeller_doesNotUpdateBalance() {
        Product product = new Product();
        product.setId(1L);
        product.setSeller(null);

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

        verify(userRepository, never()).save(any());
    }
}
