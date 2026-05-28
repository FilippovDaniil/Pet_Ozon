package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentInitResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.payment.FullPaymentService;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты InvoiceController: просмотр счёта, инициация полной оплаты, инициация BNPL.
// После рефакторинга POST /api/invoices/{id}/payments возвращает {formUrl} для редиректа,
// а не PaymentResponse как раньше. Старые тесты обновлены под новый контракт.
@WebMvcTest(
        value = InvoiceController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class InvoiceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean InvoiceService     invoiceService;
    @MockitoBean FullPaymentService fullPaymentService;
    @MockitoBean BnplService        bnplService;

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private InvoiceResponse makeInvoiceResponse(Long id, InvoiceStatus status) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(id);
        r.setOrderId(1L);
        r.setAmount(new BigDecimal("5000.00"));
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    // ── GET /api/invoices/{id} ────────────────────────────────────────────────

    @Test
    void getInvoice_found_returns200() throws Exception {
        when(invoiceService.getInvoiceById(1L))
                .thenReturn(makeInvoiceResponse(1L, InvoiceStatus.UNPAID));

        mockMvc.perform(get("/api/invoices/1").with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.amount").value(5000.00));
    }

    @Test
    void getInvoice_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/invoices/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInvoice_notFound_returns404() throws Exception {
        when(invoiceService.getInvoiceById(99L))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 99"));

        mockMvc.perform(get("/api/invoices/99").with(user(mockClientUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── POST /api/invoices/{id}/payments (полная оплата → formUrl) ────────────

    @Test
    void initiateFullPayment_unpaidInvoice_returns201WithFormUrl() throws Exception {
        PaymentInitResponse resp = new PaymentInitResponse(
                "https://alfa.rbsuat.com/form?id=abc", "alfa-abc", null);
        when(fullPaymentService.initiate(1L)).thenReturn(resp);

        mockMvc.perform(post("/api/invoices/1/payments")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formUrl").value("https://alfa.rbsuat.com/form?id=abc"))
                .andExpect(jsonPath("$.alfaOrderId").value("alfa-abc"))
                .andExpect(jsonPath("$.contractId").isEmpty());
    }

    @Test
    void initiateFullPayment_alreadyPaid_returns500() throws Exception {
        when(fullPaymentService.initiate(1L))
                .thenThrow(new IllegalStateException("Счёт #1 уже оплачен"));

        mockMvc.perform(post("/api/invoices/1/payments")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void initiateFullPayment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/invoices/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initiateFullPayment_withoutContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/invoices/1/payments")
                        .with(user(mockClientUser())))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ── POST /api/invoices/{id}/bnpl (рассрочка → formUrl) ───────────────────

    @Test
    void initiateBnpl_validProduct_returns201WithFormUrl() throws Exception {
        PaymentInitResponse resp = new PaymentInitResponse(
                "https://alfa.rbsuat.com/form?id=bnpl-001", "alfa-bnpl-001", 42L);
        when(bnplService.initiate(1L, "BIWEEKLY_4")).thenReturn(resp);

        mockMvc.perform(post("/api/invoices/1/bnpl")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bnplProduct\": \"BIWEEKLY_4\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formUrl").value("https://alfa.rbsuat.com/form?id=bnpl-001"))
                .andExpect(jsonPath("$.contractId").value(42));
    }

    @Test
    void initiateBnpl_missingProduct_returns400() throws Exception {
        // bnplProduct — обязательное поле (@NotNull)
        mockMvc.perform(post("/api/invoices/1/bnpl")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiateBnpl_invoiceNotFound_returns404() throws Exception {
        when(bnplService.initiate(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Invoice not found"));

        mockMvc.perform(post("/api/invoices/99/bnpl")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bnplProduct\": \"MONTHLY_4\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void initiateBnpl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/invoices/1/bnpl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bnplProduct\": \"BIWEEKLY_4\"}"))
                .andExpect(status().isUnauthorized());
    }
}
