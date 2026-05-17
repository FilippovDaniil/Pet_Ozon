package com.example.marketplace.controller;

import com.example.marketplace.dto.response.*;
import com.example.marketplace.service.AccountantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accountant")
@RequiredArgsConstructor
public class AccountantController {

    private final AccountantService accountantService;

    @GetMapping("/summary")
    public AccountantSummaryResponse getSummary() {
        return accountantService.getSummary();
    }

    @GetMapping("/orders")
    public List<OrderReportDto> getOrders() {
        return accountantService.getOrdersReport();
    }

    @GetMapping("/carts")
    public List<CartReportDto> getCarts() {
        return accountantService.getCartsReport();
    }

    @GetMapping("/customers")
    public List<CustomerReportDto> getCustomers() {
        return accountantService.getCustomersReport();
    }

    @GetMapping("/emails")
    public List<EmailLogDto> getEmails() {
        return accountantService.getEmailsReport();
    }
}
