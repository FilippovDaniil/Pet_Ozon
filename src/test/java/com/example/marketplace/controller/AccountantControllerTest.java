package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.*;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.AccountantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты AccountantController: проверяем HTTP-статусы и контроль доступа по роли.
// JWT-фильтр и основной SecurityConfig исключены — используется TestSecurityConfig,
// где аутентификация подставляется через .with(user(...)) без реального токена.
@WebMvcTest(
        value = AccountantController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class AccountantControllerTest {

    @Autowired MockMvc mockMvc;

    // MockitoBean подменяет реальный AccountantService бином-моком в Spring-контексте теста
    @MockitoBean AccountantService accountantService;

    // Вспомогательный метод: пользователь с ролью ACCOUNTANT
    private User mockAccountantUser() {
        User u = new User();
        u.setId(5L);
        u.setEmail("accountant@example.com");
        u.setRole(Role.ACCOUNTANT);
        return u;
    }

    // Вспомогательный метод: пользователь с ролью CLIENT — не должен иметь доступ
    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    // Вспомогательный метод: готовый SummaryResponse для мока сервиса
    private AccountantSummaryResponse makeSummary() {
        return new AccountantSummaryResponse(
                10L, 7L, new BigDecimal("350000.00"),
                15L, 5L, new BigDecimal("75000.00"),
                20L, 18L
        );
    }

    // ── GET /api/accountant/summary ───────────────────────────────────────────

    @Test
    void getSummary_asAccountant_returns200WithBody() throws Exception {
        when(accountantService.getSummary()).thenReturn(makeSummary());

        mockMvc.perform(get("/api/accountant/summary")
                        .with(user(mockAccountantUser())))
                .andExpect(status().isOk())
                // Проверяем ключевые числовые поля в JSON-ответе
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.paidOrders").value(7))
                .andExpect(jsonPath("$.totalClients").value(15))
                .andExpect(jsonPath("$.emailsSent").value(20));
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        // Без .with(user(...)) — запрос без аутентификации → 401
        mockMvc.perform(get("/api/accountant/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSummary_asClient_returns403() throws Exception {
        // CLIENT не имеет доступа к эндпоинтам /api/accountant/** → 403
        mockMvc.perform(get("/api/accountant/summary")
                        .with(user(mockClientUser())))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/accountant/orders ────────────────────────────────────────────

    @Test
    void getOrders_asAccountant_returns200WithList() throws Exception {
        OrderReportDto dto = new OrderReportDto(
                1L, "Иван Иванов", "ivan@example.com",
                LocalDateTime.now(), "PAID",
                new BigDecimal("12000.00"), 2, "Москва, ул. 1"
        );
        when(accountantService.getOrdersReport()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accountant/orders")
                        .with(user(mockAccountantUser())))
                .andExpect(status().isOk())
                // Проверяем что первый элемент массива содержит нужные поля
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Иван Иванов"))
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void getOrders_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/accountant/orders")
                        .with(user(mockClientUser())))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/accountant/carts ─────────────────────────────────────────────

    @Test
    void getCarts_asAccountant_returns200WithList() throws Exception {
        CartReportDto dto = new CartReportDto(
                "Пётр", "petr@example.com",
                "Ноутбук Dell", "Ноутбуки",
                1, new BigDecimal("90000.00"), new BigDecimal("90000.00")
        );
        when(accountantService.getCartsReport()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accountant/carts")
                        .with(user(mockAccountantUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Ноутбук Dell"))
                .andExpect(jsonPath("$[0].quantity").value(1));
    }

    // ── GET /api/accountant/customers ─────────────────────────────────────────

    @Test
    void getCustomers_asAccountant_returns200WithList() throws Exception {
        CustomerReportDto dto = new CustomerReportDto(
                1L, "Анна Петрова", "anna@example.com",
                5, new BigDecimal("45000.00"), LocalDateTime.now()
        );
        when(accountantService.getCustomersReport()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accountant/customers")
                        .with(user(mockAccountantUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Анна Петрова"))
                .andExpect(jsonPath("$[0].ordersCount").value(5));
    }

    // ── GET /api/accountant/emails ────────────────────────────────────────────

    @Test
    void getEmails_asAccountant_returns200WithList() throws Exception {
        EmailLogDto dto = new EmailLogDto(
                1L, "buyer@example.com",
                "Чек об оплате — Заказ #1",
                LocalDateTime.now(), true, null
        );
        when(accountantService.getEmailsReport()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/accountant/emails")
                        .with(user(mockAccountantUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipient").value("buyer@example.com"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void getEmails_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/accountant/emails"))
                .andExpect(status().isUnauthorized());
    }
}
