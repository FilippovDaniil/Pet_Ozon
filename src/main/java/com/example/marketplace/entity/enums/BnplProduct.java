package com.example.marketplace.entity.enums;

import java.math.BigDecimal;

/**
 * Продукт рассрочки BNPL — фиксированный набор условий, заданный прямо в enum.
 *
 * Каждая константа несёт свои параметры (число взносов, ставка комиссии, интервал,
 * описание), поэтому сервису не нужны if/switch — он читает поля напрямую из продукта.
 * Комиссия = доля от суммы заказа (0.10 = 10%), добавляется к общей сумме рассрочки.
 */
public enum BnplProduct {

    // installmentCount, commissionRate, intervalDays, description
    BIWEEKLY_4(4, BigDecimal.ZERO,                   14, "4 платежа по 25% — раз в 2 недели"),  // 0% комиссии
    MONTHLY_4 (4, new BigDecimal("0.10"),             30, "4 платежа, комиссия 10% — раз в месяц"),
    MONTHLY_6 (6, new BigDecimal("0.15"),             30, "6 платежей, комиссия 15% — раз в месяц");

    public final int    installmentCount;   // сколько взносов в графике
    public final BigDecimal commissionRate;  // ставка комиссии (доля: 0.15 = 15%)
    /** интервал между платежами в днях */
    public final int    intervalDays;
    public final String description;         // человекочитаемое описание для UI

    BnplProduct(int installmentCount, BigDecimal commissionRate, int intervalDays, String description) {
        this.installmentCount = installmentCount;
        this.commissionRate   = commissionRate;
        this.intervalDays     = intervalDays;
        this.description      = description;
    }
}
