package com.example.marketplace.entity.enums;

/**
 * Статус счёта (Invoice) на оплату.
 *
 * Invoice создаётся автоматически при оформлении заказа (CartService.checkout).
 * Изначально всегда UNPAID.
 * После успешной оплаты (InvoiceService.pay) переходит в PAID.
 * FAILED — зарезервировано для будущей интеграции с платёжным шлюзом,
 * когда платёж может быть отклонён банком.
 */
public enum InvoiceStatus {
    UNPAID,  // счёт выставлен, ждёт оплаты
    PAID,    // успешно оплачен
    FAILED   // оплата не прошла (заглушка для будущего)
}
