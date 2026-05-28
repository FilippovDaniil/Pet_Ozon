package com.example.marketplace.dto.response;

import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.PaymentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ответ с данными заказа.
 *
 * Включает invoiceId — удобно для клиента: сразу знает, по какому счёту платить.
 * invoiceId может быть null в теории (если Invoice ещё не создан), но на практике
 * Invoice всегда создаётся вместе с Order в CartService.checkout().
 */
@Data
public class OrderResponse {
    private Long id;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private List<OrderItemResponse> items;
    private Long invoiceId;
    private PaymentType paymentType;
    private Long bnplContractId;  // null для FULL-заказов
}
