package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Тесты EmailController (POST /api/admin/email/send).
// Проверяем: авторизацию, валидацию входных данных, делегирование в EmailService.
@WebMvcTest(
        value = EmailController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class EmailControllerTest {

    @Autowired MockMvc mockMvc;

    // EmailService заменён моком — реальная отправка писем не происходит
    @MockitoBean EmailService emailService;

    private User mockAdminUser() {
        User u = new User();
        u.setId(2L);
        u.setEmail("admin@example.com");
        u.setRole(Role.ADMIN);
        return u;
    }

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    // Вспомогательный метод: формирует корректный JSON-запрос на отправку письма
    private String validBody() {
        return """
                {
                  "to":      "recipient@example.com",
                  "subject": "Важное сообщение",
                  "text":    "Текст письма"
                }
                """;
    }

    // ── POST /api/admin/emails ────────────────────────────────────────────────

    @Test
    void send_asAdmin_returns200AndCallsService() throws Exception {
        doNothing().when(emailService).sendCustomEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());

        // Проверяем что сервис действительно был вызван с правильными аргументами
        verify(emailService).sendCustomEmail("recipient@example.com", "Важное сообщение", "Текст письма");
    }

    @Test
    void send_unauthenticated_returns401() throws Exception {
        // Без авторизации — 401
        mockMvc.perform(post("/api/admin/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void send_asClient_returns403() throws Exception {
        // Клиент не имеет доступа к /api/admin/** → 403
        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_invalidEmail_returns400() throws Exception {
        // Поле to с невалидным email-адресом — @Email должен отклонить запрос
        String badBody = """
                {
                  "to":      "not-an-email",
                  "subject": "Тема",
                  "text":    "Текст"
                }
                """;

        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());

        // Сервис не должен вызываться при невалидных данных
        verify(emailService, never()).sendCustomEmail(anyString(), anyString(), anyString());
    }

    @Test
    void send_missingSubject_returns400() throws Exception {
        // Отсутствует обязательное поле subject (@NotBlank)
        String badBody = """
                {
                  "to":   "recipient@example.com",
                  "text": "Текст письма"
                }
                """;

        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_blankText_returns400() throws Exception {
        // Поле text есть, но пустое (@NotBlank не принимает строки из пробелов)
        String badBody = """
                {
                  "to":      "recipient@example.com",
                  "subject": "Тема",
                  "text":    "   "
                }
                """;

        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_serviceThrowsException_returns500() throws Exception {
        // Если EmailService бросает RuntimeException (SMTP недоступен) — возвращаем 500
        doThrow(new RuntimeException("Ошибка отправки письма: SMTP timeout"))
                .when(emailService).sendCustomEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/admin/emails")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isInternalServerError());
    }
}
