package com.example.marketplace.dto.response;

import java.time.LocalDate;

public record BnplInstallmentResponse(
        Long       id,
        Integer    installmentNumber,
        Long       amountKopecks,
        LocalDate  dueDate,
        String     status,
        String     paidAt,
        Integer    daysPostponed,     // суммарное количество уже использованных дней переноса
        Integer    daysPostponeLeft   // сколько дней переноса ещё доступно (14 - daysPostponed)
) {}
