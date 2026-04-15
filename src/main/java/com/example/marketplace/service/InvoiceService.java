package com.example.marketplace.service;

import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentResponse;
import com.example.marketplace.entity.Invoice;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.Payment;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.PaymentStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.InvoiceRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse getInvoiceById(Long id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    public PaymentResponse pay(Long invoiceId, String paymentMethod) {
        Invoice invoice = findEntityById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("Invoice #" + invoiceId + " is already paid");
        }

        // Mark invoice as paid
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        // Mark linked order as paid
        Order order = invoice.getOrder();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // Record payment
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getAmount());
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
