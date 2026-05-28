package com.example.marketplace.entity.enums;

public enum BnplInstallmentStatus {
    PENDING,   // ожидает наступления даты платежа
    PAID,      // успешно списан
    OVERDUE,   // дата прошла, деньги не списаны
    CANCELLED  // контракт отменён до списания
}
