package com.example.marketplace.controller;

import com.example.marketplace.dto.request.PaymentRequest;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentResponse;
import com.example.marketplace.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/invoice", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/{id}")
    public InvoiceResponse getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }

    @PostMapping(value = "/{invoiceId}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PaymentResponse pay(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) PaymentRequest request) {
        String method = (request != null) ? request.getPaymentMethod() : "CARD";
        return invoiceService.pay(invoiceId, method);
    }
}
