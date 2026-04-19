package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.PaymentResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.PaymentStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
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

    @MockitoBean InvoiceService invoiceService;

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

    private PaymentResponse makePaymentResponse(String method) {
        PaymentResponse r = new PaymentResponse();
        r.setId(1L);
        r.setInvoiceId(1L);
        r.setAmount(new BigDecimal("5000.00"));
        r.setPaymentMethod(method);
        r.setStatus(PaymentStatus.SUCCESS);
        r.setTimestamp(LocalDateTime.now());
        return r;
    }

    // ── GET /api/invoice/{id} ─────────────────────────────────────────────────

    @Test
    void getInvoice_found_returns200() throws Exception {
        when(invoiceService.getInvoiceById(1L))
                .thenReturn(makeInvoiceResponse(1L, InvoiceStatus.UNPAID));

        mockMvc.perform(get("/api/invoice/1").with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void getInvoice_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/invoice/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInvoice_notFound_returns404() throws Exception {
        when(invoiceService.getInvoiceById(99L))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 99"));

        mockMvc.perform(get("/api/invoice/99").with(user(mockClientUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Invoice not found with id: 99"));
    }

    // ── POST /api/invoice/{id}/pay ────────────────────────────────────────────

    @Test
    void pay_withCardMethod_returns200WithPayment() throws Exception {
        when(invoiceService.pay(1L, "CARD")).thenReturn(makePaymentResponse("CARD"));

        mockMvc.perform(post("/api/invoice/1/pay")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(5000.00));
    }

    @Test
    void pay_withCashMethod_returns200() throws Exception {
        when(invoiceService.pay(1L, "CASH")).thenReturn(makePaymentResponse("CASH"));

        mockMvc.perform(post("/api/invoice/1/pay")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("CASH"));
    }

    @Test
    void pay_withoutBody_returns415() throws Exception {
        // @RequestBody с consumes=application/json: отсутствие Content-Type → 415
        mockMvc.perform(post("/api/invoice/1/pay")
                        .with(user(mockClientUser())))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void pay_withBlankPaymentMethod_returns400() throws Exception {
        // paymentMethod пустой → @NotBlank → MethodArgumentNotValidException → 400
        mockMvc.perform(post("/api/invoice/1/pay")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void pay_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/invoice/1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pay_alreadyPaid_returns400() throws Exception {
        when(invoiceService.pay(1L, "CARD"))
                .thenThrow(new IllegalArgumentException("Invoice #1 is already paid"));

        mockMvc.perform(post("/api/invoice/1/pay")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invoice #1 is already paid"));
    }

    @Test
    void pay_invoiceNotFound_returns404() throws Exception {
        when(invoiceService.pay(99L, "CARD"))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 99"));

        mockMvc.perform(post("/api/invoice/99/pay")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isNotFound());
    }
}
