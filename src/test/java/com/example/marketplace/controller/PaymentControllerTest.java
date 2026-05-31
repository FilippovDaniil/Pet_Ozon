package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.payment.FullPaymentService;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты PaymentController: публичный callback и fail endpoint.
 * Эти endpoint-ы не требуют аутентификации — банк делает browser-redirect без JWT.
 * SecurityConfig и JWT-фильтр исключены, вместо них — TestSecurityConfig.
 */
@WebMvcTest(
        value = PaymentController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FullPaymentService fullPaymentService;
    @MockitoBean BnplService bnplService;
    @MockitoBean CardService cardService;  // PaymentController зависит от него (card-bind-callback)

    // ── GET /api/payment/callback ─────────────────────────────────────────────

    @Test
    void callback_fullPaymentSuccess_returnsSuccessHtml() throws Exception {
        // BNPL не распознаёт orderId → IllegalArgumentException → падаём на fullPayment
        when(bnplService.confirmPreAuth("alfa-123"))
                .thenThrow(new IllegalArgumentException("Not a BNPL pre-auth"));
        when(bnplService.confirmInstallmentForm("alfa-123"))
                .thenThrow(new IllegalArgumentException("Not a BNPL installment form"));
        when(bnplService.confirmPostponeForm("alfa-123"))
                .thenThrow(new IllegalArgumentException("Not a postpone form"));
        when(fullPaymentService.confirm("alfa-123")).thenReturn("paid");

        mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата прошла успешно")));
    }

    // Полная оплата отклонена → HTML с сообщением «Оплата отклонена».
    @Test
    void callback_fullPaymentFailed_returnsFailHtml() throws Exception {
        when(bnplService.confirmPreAuth("alfa-456"))
                .thenThrow(new IllegalArgumentException("Not a BNPL pre-auth"));
        when(bnplService.confirmInstallmentForm("alfa-456"))
                .thenThrow(new IllegalArgumentException("Not a BNPL installment form"));
        when(bnplService.confirmPostponeForm("alfa-456"))
                .thenThrow(new IllegalArgumentException("Not a postpone form"));
        when(fullPaymentService.confirm("alfa-456")).thenReturn("failed");

        mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-456"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата отклонена")));
    }

    @Test
    void callback_bnplPreAuthSuccess_returnsSuccessHtml() throws Exception {
        // BNPL распознаёт orderId → возвращает "paid"
        when(bnplService.confirmPreAuth("alfa-bnpl-789")).thenReturn("paid");

        mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-bnpl-789"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата прошла успешно")));
        // fullPaymentService не должен вызываться
        verify(fullPaymentService, never()).confirm(anyString());
    }

    // Платёж ещё не финализирован → сообщение «обрабатывается».
    @Test
    void callback_pending_returnsPendingMessage() throws Exception {
        when(bnplService.confirmPreAuth("alfa-pend"))
                .thenThrow(new IllegalArgumentException("Not a BNPL pre-auth"));
        when(bnplService.confirmInstallmentForm("alfa-pend"))
                .thenThrow(new IllegalArgumentException("Not a BNPL installment form"));
        when(bnplService.confirmPostponeForm("alfa-pend"))
                .thenThrow(new IllegalArgumentException("Not a postpone form"));
        when(fullPaymentService.confirm("alfa-pend")).thenReturn("pending");

        mockMvc.perform(get("/api/payment/callback").param("orderId", "alfa-pend"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("обрабатывается")));
    }

    @Test
    void callback_noOrderId_returnsErrorResponse() throws Exception {
        // Без orderId параметра — Spring MVC бросает MissingServletRequestParameterException
        // GlobalExceptionHandler обрабатывает её как 4xx или 5xx в зависимости от конфигурации
        mockMvc.perform(get("/api/payment/callback"))
                .andExpect(status().is5xxServerError()); // GlobalExceptionHandler → 500 при отсутствии param
    }

    @Test
    void callback_accessibleWithoutAuth() throws Exception {
        // Callback публичный — банк делает redirect без JWT
        when(bnplService.confirmPreAuth(anyString()))
                .thenThrow(new IllegalArgumentException("Not BNPL"));
        when(bnplService.confirmInstallmentForm(anyString()))
                .thenThrow(new IllegalArgumentException("Not a BNPL installment form"));
        when(bnplService.confirmPostponeForm(anyString()))
                .thenThrow(new IllegalArgumentException("Not a postpone form"));
        when(fullPaymentService.confirm(anyString())).thenReturn("paid");

        mockMvc.perform(get("/api/payment/callback").param("orderId", "any-order"))
                .andExpect(status().isOk()); // не 401
    }

    // ── GET /api/payment/fail ─────────────────────────────────────────────────

    // fail-endpoint с orderId → HTML «Оплата не завершена».
    @Test
    void fail_withOrderId_returnsFailHtml() throws Exception {
        mockMvc.perform(get("/api/payment/fail").param("orderId", "alfa-000"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата не завершена")));
    }

    @Test
    void fail_withoutOrderId_returnsFailHtml() throws Exception {
        // orderId — необязательный параметр на fail endpoint
        mockMvc.perform(get("/api/payment/fail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата не завершена")));
    }

    @Test
    void fail_accessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/payment/fail"))
                .andExpect(status().isOk()); // не 401
    }
}
