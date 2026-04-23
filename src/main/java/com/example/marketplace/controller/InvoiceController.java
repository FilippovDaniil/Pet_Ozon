package com.example.marketplace.controller;

import com.example.marketplace.dto.request.PaymentRequest;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentResponse;
import com.example.marketplace.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для работы со счетами и оплатой.
 *
 * Требует аутентификации. Доступен любому вошедшему пользователю.
 *
 * Эндпоинты:
 *   GET  /api/invoice/{id}          — посмотреть счёт
 *   POST /api/invoice/{id}/pay      — оплатить счёт
 *
 * После оплаты InvoiceService.pay() записывает Payment и обновляет
 * статусы Invoice (PAID) и Order (PAID), а также начисляет деньги продавцам.
 */
@RestController
@RequestMapping(value = "/api/invoice", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /** GET /api/invoice/{id} — получить информацию о счёте. */
    @GetMapping("/{id}")
    public InvoiceResponse getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }

    /**
     * POST /api/invoice/{invoiceId}/pay — оплатить счёт.
     * Тело запроса: {"paymentMethod": "CARD"}
     * Возвращает: данные о созданном платеже.
     */
    @PostMapping(value = "/{invoiceId}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PaymentResponse pay(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request) {
        return invoiceService.pay(invoiceId, request.getPaymentMethod());
    }
}
