package com.example.marketplace.dto.response;

import com.example.marketplace.entity.enums.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ответ с данными счёта на оплату.
 *
 * paidAt будет null, пока счёт не оплачен (status = UNPAID).
 * После оплаты — timestamp момента платежа.
 *
 * Jackson по умолчанию сериализует null-поля как null в JSON.
 * На production часто добавляют @JsonInclude(NON_NULL) чтобы пустые поля не попадали в ответ.
 */
@Data
public class InvoiceResponse {
    private Long id;
    private Long orderId;     // к какому заказу относится счёт
    private BigDecimal amount;
    private InvoiceStatus status;  // UNPAID / PAID / FAILED
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;  // null до оплаты
}
