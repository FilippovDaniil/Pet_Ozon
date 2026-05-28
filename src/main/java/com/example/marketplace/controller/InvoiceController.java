package com.example.marketplace.controller;

import com.example.marketplace.dto.request.InitiatePaymentRequest;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.payment.FullPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.example.marketplace.service.InvoiceService;

/**
 * Контроллер счетов и оплаты.
 *
 *   GET  /api/invoices/{id}           — посмотреть счёт
 *   POST /api/invoices/{id}/payments  — инициировать полную оплату → formUrl (201)
 *   POST /api/invoices/{id}/bnpl      — инициировать BNPL-рассрочку → formUrl (201)
 */
@RestController
@RequestMapping(value = "/api/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService    invoiceService;
    private final FullPaymentService fullPaymentService;
    private final BnplService        bnplService;

    @GetMapping("/{id}")
    public InvoiceResponse getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }

    /** Инициирует одностадийную полную оплату. Возвращает formUrl для редиректа клиента. */
    @PostMapping(value = "/{invoiceId}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentInitResponse initiateFullPayment(@PathVariable Long invoiceId) {
        return fullPaymentService.initiate(invoiceId);
    }

    /** Инициирует BNPL-рассрочку (pre-auth на полную сумму). Возвращает formUrl. */
    @PostMapping(value = "/{invoiceId}/bnpl", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentInitResponse initiateBnpl(
            @PathVariable Long invoiceId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        return bnplService.initiate(invoiceId, request.bnplProduct());
    }
}
