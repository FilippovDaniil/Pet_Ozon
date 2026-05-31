package com.example.marketplace.entity.enums;

/**
 * Жизненный цикл заказа.
 *
 * Для BNPL-заказов статус автоматически выводится из поштучных счётчиков позиций
 * (BnplService.recalcOrderStatus): CREATED → частичные/ISSUED → финал.
 * Для FULL-оплаты — классика: CREATED → (оплата) → PAID.
 *
 *   Начальный:     CREATED
 *   Промежуточные: PARTIALLY_ISSUED, PARTIALLY_CANCELLED, PARTIALLY_RETURNED, ISSUED
 *   Финальные:     PAID, RETURNED, CANCELLED  (заказ скрывается в ЛК клиента)
 *                  DELIVERED — legacy-финал (ручная отметка админом; авто-расчёт его не ставит)
 */
public enum OrderStatus {
    CREATED,              // заказ создан, ожидает оплаты / выдачи

    PARTIALLY_ISSUED,     // часть единиц выдана, часть ещё ожидает
    PARTIALLY_CANCELLED,  // часть единиц отменена, выданных пока нет
    PARTIALLY_RETURNED,   // были возвраты, но есть ещё активные единицы
    ISSUED,               // все единицы выданы, но рассрочка ещё не выплачена полностью

    PAID,                 // оплачено полностью (FULL-оплата или BNPL COMPLETED) — финал
    RETURNED,             // все единицы возвращены/отменены с возвратом — финал
    CANCELLED,            // заказ отменён — финал
    DELIVERED             // доставлен (legacy, ручная отметка) — финал
}
