package com.example.marketplace.dto.response;

import java.time.LocalDate;

/**
 * Одна строка графика рассрочки (взнос) для отображения клиенту/админу.
 */
public record BnplInstallmentResponse(
        Long       id,
        Integer    installmentNumber,  // порядковый номер взноса (1..N)
        Long       amountKopecks,      // сумма взноса в копейках
        LocalDate  dueDate,            // плановая дата списания
        String     status,             // BnplInstallmentStatus
        String     paidAt,             // фактический момент оплаты (null если не оплачен)
        Integer    daysPostponed,     // суммарное количество уже использованных дней переноса
        Integer    daysPostponeLeft   // сколько дней переноса ещё доступно (14 - daysPostponed)
) {}
