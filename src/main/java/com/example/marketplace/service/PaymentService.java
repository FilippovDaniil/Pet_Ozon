package com.example.marketplace.service;

import com.example.marketplace.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Сервис-заглушка для работы с платежами.
 *
 * Основная логика оплаты сейчас находится в InvoiceService.pay().
 * Этот класс создан «на вырост» — здесь будут располагаться:
 *   - история платежей конкретного пользователя,
 *   - повторные попытки оплаты,
 *   - возвраты (refund),
 *   - интеграция с внешним платёжным шлюзом.
 *
 * Пока пустой, но уже подключён к Spring-контейнеру через @Service.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
}
