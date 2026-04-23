package com.example.marketplace.entity.enums;

/**
 * Жизненный цикл заказа:
 *
 *   CREATED → (оплата) → PAID → (отправка) → DELIVERED
 *                           ↓
 *                        CANCELLED
 *
 * Переходы между статусами управляются AdminController (PUT /api/admin/orders/{id}/status)
 * и InvoiceService.pay() — который переводит CREATED → PAID автоматически при оплате.
 */
public enum OrderStatus {
    CREATED,    // заказ создан, ожидает оплаты
    PAID,       // счёт оплачен (Invoice.status = PAID)
    CANCELLED,  // заказ отменён
    DELIVERED   // заказ доставлен покупателю
}
