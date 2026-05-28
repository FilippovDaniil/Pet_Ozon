package com.example.marketplace.entity.enums;

import java.math.BigDecimal;

public enum BnplProduct {

    BIWEEKLY_4(4, BigDecimal.ZERO,                   14, "4 платежа по 25% — раз в 2 недели"),
    MONTHLY_4 (4, new BigDecimal("0.10"),             30, "4 платежа, комиссия 10% — раз в месяц"),
    MONTHLY_6 (6, new BigDecimal("0.15"),             30, "6 платежей, комиссия 15% — раз в месяц");

    public final int    installmentCount;
    public final BigDecimal commissionRate;
    /** интервал между платежами в днях */
    public final int    intervalDays;
    public final String description;

    BnplProduct(int installmentCount, BigDecimal commissionRate, int intervalDays, String description) {
        this.installmentCount = installmentCount;
        this.commissionRate   = commissionRate;
        this.intervalDays     = intervalDays;
        this.description      = description;
    }
}
