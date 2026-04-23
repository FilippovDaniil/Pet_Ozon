package com.example.marketplace.entity.enums;

/**
 * Статус конкретного платёжного документа (Payment).
 *
 * Один Invoice может теоретически иметь несколько Payment-ов
 * (например, первая попытка — FAILED, вторая — SUCCESS).
 * Сейчас InvoiceService.pay() всегда создаёт одну запись со статусом SUCCESS.
 */
public enum PaymentStatus {
    SUCCESS,  // платёж прошёл
    FAILED    // платёж отклонён (заглушка)
}
