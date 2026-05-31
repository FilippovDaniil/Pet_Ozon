package com.example.marketplace.dto.response;

import java.math.BigDecimal;

/**
 * Сводка по BNPL-рассрочкам (Альфа Банк) для дашборда бухгалтера.
 * Ответ на GET /api/accountant/bnpl. Суммы — в рублях.
 *
 *   financedRub    — профинансировано (Σ суммы по действующим контрактам ACTIVE+COMPLETED)
 *   commissionRub  — заработок на комиссии рассрочки (Σ commission по тем же контрактам)
 *   receivedRub    — фактически получено от клиентов (Σ депозитов)
 *   outstandingRub — ожидается к получению по активным рассрочкам (financed − received, только ACTIVE)
 *   *Count/*Rub по методам — журнал списаний BnplPayment: FIRST / SCHEDULED / MANUAL
 */
public record AccountantBnplResponse(
        long activeContracts,
        long completedContracts,
        long awaitingContracts,
        long cancelledContracts,
        BigDecimal financedRub,
        BigDecimal commissionRub,
        BigDecimal receivedRub,
        BigDecimal outstandingRub,
        long paymentsCount,
        BigDecimal paymentsTotalRub,
        long firstCount,    BigDecimal firstRub,
        long scheduledCount, BigDecimal scheduledRub,
        long manualCount,    BigDecimal manualRub
) {}
