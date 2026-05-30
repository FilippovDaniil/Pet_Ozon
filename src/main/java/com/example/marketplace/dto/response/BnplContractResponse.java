package com.example.marketplace.dto.response;

import java.util.List;

/**
 * Полное состояние BNPL-контракта для фронта (клиент и админ).
 *
 * Объединяет шапку контракта, график взносов (installments) и журнал
 * фактических списаний (payments) в одном объекте, чтобы UI не делал
 * несколько запросов.
 */
public record BnplContractResponse(
        Long                          id,
        Long                          orderId,
        String                        product,               // имя enum BnplProduct
        String                        productDescription,    // человекочитаемое описание продукта
        Long                          totalAmountKopecks,    // итог к оплате (заказ + комиссия)
        Long                          commissionKopecks,     // сумма комиссии BNPL
        Integer                       installmentCount,      // число взносов в графике
        String                        status,                // BnplContractStatus
        Long                          depositedAmountKopecks,// сколько уже реально списано
        List<BnplInstallmentResponse> installments,          // график платежей
        List<BnplPaymentResponse>     payments               // журнал фактических транзакций
) {}
