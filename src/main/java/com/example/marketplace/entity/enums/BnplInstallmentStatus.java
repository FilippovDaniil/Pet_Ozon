package com.example.marketplace.entity.enums;

/**
 * Статус одного взноса (строки графика) внутри BNPL-контракта.
 *
 * PENDING → PAID при успешном списании; PENDING → OVERDUE если дата прошла;
 * PENDING → CANCELLED при отмене контракта до наступления даты.
 */
public enum BnplInstallmentStatus {
    PENDING,   // ожидает наступления даты платежа
    PAID,      // успешно списан
    OVERDUE,   // дата прошла, деньги не списаны
    CANCELLED  // контракт отменён до списания
}
