package com.example.marketplace.service;

import com.example.marketplace.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stub-сервис для работы с платежами.
 * Основная логика оплаты находится в InvoiceService.
 * Здесь можно добавить: историю платежей пользователя,
 * возвраты, повторные попытки оплаты и т.д.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
}
