package com.example.marketplace.entity.enums;

public enum ItemStatus {
    PENDING_ISSUE,  // ожидает выдачи (начальный статус для BNPL)
    ISSUED,         // товар выдан клиенту
    CANCELLED,      // отменён до выдачи (reverse на долю товара)
    RETURNED        // возвращён после выдачи (refund на долю товара)
}
