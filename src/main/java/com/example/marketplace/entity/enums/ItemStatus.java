package com.example.marketplace.entity.enums;

/**
 * Статус отдельной позиции заказа в рамках BNPL.
 *
 * Управляет частичными операциями в шлюзе: ISSUED → deposit.do на долю товара,
 * CANCELLED → reverse.do (до выдачи), RETURNED → refund.do (после выдачи).
 */
public enum ItemStatus {
    PENDING_ISSUE,  // ожидает выдачи (начальный статус для BNPL)
    ISSUED,         // товар выдан клиенту
    CANCELLED,      // отменён до выдачи (reverse на долю товара)
    RETURNED        // возвращён после выдачи (refund на долю товара)
}
