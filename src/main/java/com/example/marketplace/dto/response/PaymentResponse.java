package com.example.marketplace.dto.response;

import com.example.marketplace.entity.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ответ после попытки оплаты (POST /api/invoice/{id}/pay).
 *
 * Возвращается из InvoiceService.pay() и передаётся клиенту как подтверждение платежа.
 * Содержит статус платежа и timestamp — клиент может показать квитанцию.
 */
@Data
public class PaymentResponse {
    private Long id;
    private Long invoiceId;
    private BigDecimal amount;
    private String paymentMethod;   // "CARD", "SBP" и т.д.
    private PaymentStatus status;   // SUCCESS / FAILED
    private LocalDateTime timestamp;
}
