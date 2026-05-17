package com.example.marketplace.controller;

import com.example.marketplace.dto.response.*;
import com.example.marketplace.service.AccountantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Контроллер отчётов для бухгалтера. Предоставляет 5 эндпоинтов только для чтения.
// Доступ ограничен двойной защитой:
// 1. SecurityConfig: .requestMatchers("/api/accountant/**").hasRole("ACCOUNTANT") — уровень URL
// 2. @PreAuthorize("hasRole('ACCOUNTANT')") в AccountantService — уровень бина
@RestController
// Все методы класса обрабатывают запросы по пути /api/accountant и его подпутям
@RequestMapping("/api/accountant")
// Lombok: генерирует конструктор для final-поля accountantService.
// Spring автоматически подставляет нужный бин при создании контроллера.
@RequiredArgsConstructor
public class AccountantController {

    // AccountantService содержит всю логику формирования отчётов и агрегацию данных
    private final AccountantService accountantService;

    // GET /api/accountant/summary — сводные KPI: заказы, выручка, клиенты, письма.
    // Возвращает один объект с 8 метриками — основная карточка дашборда бухгалтера.
    @GetMapping("/summary")
    public AccountantSummaryResponse getSummary() {
        return accountantService.getSummary();
    }

    // GET /api/accountant/orders — полный список всех заказов, отсортированных от новых к старым.
    // Каждый элемент содержит данные покупателя, сумму, статус и адрес доставки.
    @GetMapping("/orders")
    public List<OrderReportDto> getOrders() {
        return accountantService.getOrdersReport();
    }

    // GET /api/accountant/carts — все товары, добавленные в корзины, но ещё не купленные.
    // Показывает потенциальный спрос: что и у кого «лежит» в незавершённой корзине.
    @GetMapping("/carts")
    public List<CartReportDto> getCarts() {
        return accountantService.getCartsReport();
    }

    // GET /api/accountant/customers — список всех покупателей с суммой потраченных средств.
    // Помогает выявить наиболее активных клиентов и анализировать LTV.
    @GetMapping("/customers")
    public List<CustomerReportDto> getCustomers() {
        return accountantService.getCustomersReport();
    }

    // GET /api/accountant/emails — история всех попыток отправки писем (успех/ошибка).
    // Отсортировано от новых к старым. Удобно для диагностики сбоев SMTP.
    @GetMapping("/emails")
    public List<EmailLogDto> getEmails() {
        return accountantService.getEmailsReport();
    }
}
