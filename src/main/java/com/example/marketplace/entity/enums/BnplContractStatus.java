package com.example.marketplace.entity.enums;

public enum BnplContractStatus {
    AWAITING_PAYMENT,  // pre-auth создан, ждём прохождения формы банка
    ACTIVE,            // первый взнос депозирован, график запущен
    COMPLETED,         // все взносы погашены
    CANCELLED          // все товары отменены / платёж отклонён
}
