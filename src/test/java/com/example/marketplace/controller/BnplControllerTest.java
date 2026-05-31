package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.BnplContractResponse;
import com.example.marketplace.dto.response.BnplInstallmentResponse;
import com.example.marketplace.dto.response.BnplPayResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.payment.BnplService;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты BnplController: список контрактов, перенос взноса, досрочная оплата, статусы позиций.
 * Валидация тела (@Valid) проверяется отдельно от бизнес-ошибок сервиса (мокается BnplService).
 */
@WebMvcTest(
        value = BnplController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class BnplControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BnplService bnplService;
    @MockitoBean UserRepository userRepository;

    private User mockUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@test.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private BnplInstallmentResponse makeInstallmentResponse(int number, String status, int daysPostponed) {
        return new BnplInstallmentResponse(
                (long) number, number, 200_00L, LocalDate.now().plusDays(7),
                status, null, daysPostponed, 14 - daysPostponed);
    }

    // ── GET /api/bnpl/my ──────────────────────────────────────────────────────

    // Авторизованный клиент получает массив своих контрактов.
    @Test
    void myContracts_authenticated_returnsList() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(bnplService.getContractsForUser(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/bnpl/my").with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // Без аутентификации — 401.
    @Test
    void myContracts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/bnpl/my"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/bnpl/{id}/postpone ──────────────────────────────────────────

    // Перенос с привязанной картой → тихое списание комиссии → 200 + обновлённый график.
    @Test
    void postpone_validDays_charged_returns200WithInstallments() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));

        BnplPayResponse charged = BnplPayResponse.charged(List.of(makeInstallmentResponse(2, "PENDING", 3)));
        when(bnplService.postponeInstallment(eq(10L), eq(3), eq(user))).thenReturn(charged);

        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 3}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].daysPostponed").value(3))
                .andExpect(jsonPath("$.installments[0].daysPostponeLeft").value(11))
                .andExpect(jsonPath("$.installments[0].status").value("PENDING"));
    }

    // Перенос без привязки → оплата комиссии через форму банка → 200 + formUrl.
    @Test
    void postpone_noBinding_returns200WithFormUrl() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(bnplService.postponeInstallment(eq(10L), eq(3), eq(user)))
                .thenReturn(BnplPayResponse.redirect("https://bank/pay/pstp"));

        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 3}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formUrl").value("https://bank/pay/pstp"));
    }

    @Test
    void postpone_daysTooSmall_returns400() throws Exception {
        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 1}") // меньше минимума (3)
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postpone_daysTooLarge_returns400() throws Exception {
        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 20}") // больше максимума (14)
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isBadRequest());
    }

    // Превышен суммарный лимит переноса (бизнес-ошибка сервиса) → 500.
    @Test
    void postpone_limitExceeded_returns500() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(bnplService.postponeInstallment(eq(10L), eq(5), eq(user)))
                .thenThrow(new IllegalStateException("Превышен лимит переноса"));

        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 5}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isInternalServerError());
    }

    // Перенос без аутентификации — 401.
    @Test
    void postpone_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/bnpl/10/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 3}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/bnpl/{id}/pay ───────────────────────────────────────────────

    // Есть связка, пустое тело → тихое списание ближайшего взноса; 201 + installments.
    @Test
    void payNow_noBody_chargesAndReturnsInstallments() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));

        BnplInstallmentResponse paidInst = makeInstallmentResponse(2, "PAID", 0);
        when(bnplService.payInstallmentByClient(eq(10L), isNull(), eq(user)))
                .thenReturn(BnplPayResponse.charged(List.of(paidInst)));

        mockMvc.perform(post("/api/bnpl/10/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formUrl").doesNotExist())
                .andExpect(jsonPath("$.installments").isArray())
                .andExpect(jsonPath("$.installments.length()").value(1))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"));
    }

    // Есть связка, произвольная сумма покрывает несколько взносов → несколько PAID.
    @Test
    void payNow_withAmount_returnsMultiplePaid() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));

        List<BnplInstallmentResponse> paid = List.of(
                makeInstallmentResponse(2, "PAID", 0),
                makeInstallmentResponse(3, "PAID", 0)
        );
        when(bnplService.payInstallmentByClient(eq(10L), eq(40_000L), eq(user)))
                .thenReturn(BnplPayResponse.charged(paid));

        mockMvc.perform(post("/api/bnpl/10/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountKopecks\": 40000}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installments.length()").value(2));
    }

    // Нет реальной связки → банк отдаёт форму: 201 + formUrl (клиент редиректится).
    @Test
    void payNow_noBinding_returnsFormUrl() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(bnplService.payInstallmentByClient(eq(10L), isNull(), eq(user)))
                .thenReturn(BnplPayResponse.redirect("https://alfa.rbsuat.com/payment/form?orderId=abc"));

        mockMvc.perform(post("/api/bnpl/10/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formUrl").value("https://alfa.rbsuat.com/payment/form?orderId=abc"))
                .andExpect(jsonPath("$.installments").doesNotExist());
    }

    // Бизнес-ошибка сервиса (контракт не активен) → 500.
    @Test
    void payNow_inactiveContract_returns500() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(bnplService.payInstallmentByClient(eq(10L), isNull(), eq(user)))
                .thenThrow(new IllegalStateException("Оплата недоступна: контракт не активен"));

        mockMvc.perform(post("/api/bnpl/10/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isInternalServerError());
    }

    // ── PATCH /api/orders/{id}/items/{itemId} ─────────────────────────────────

    // status=ISSUED → делегирование issueUnits (кол-во по умолчанию = 1).
    @Test
    void updateItemStatus_issue_callsIssueUnits() throws Exception {
        doNothing().when(bnplService).issueUnits(5L, 50L, 1);

        mockMvc.perform(patch("/api/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ISSUED\"}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk());

        verify(bnplService).issueUnits(5L, 50L, 1);
    }

    // status=CANCELLED → делегирование cancelUnits.
    @Test
    void updateItemStatus_cancel_callsCancelUnits() throws Exception {
        doNothing().when(bnplService).cancelUnits(5L, 50L, 1);

        mockMvc.perform(patch("/api/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk());

        verify(bnplService).cancelUnits(5L, 50L, 1);
    }

    // status=RETURNED → делегирование returnUnits.
    @Test
    void updateItemStatus_return_callsReturnUnits() throws Exception {
        doNothing().when(bnplService).returnUnits(5L, 50L, 1);

        mockMvc.perform(patch("/api/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RETURNED\"}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk());

        verify(bnplService).returnUnits(5L, 50L, 1);
    }

    // Явное кол-во единиц в теле → прокидывается в сервис.
    @Test
    void updateItemStatus_withQuantity_passesCount() throws Exception {
        doNothing().when(bnplService).issueUnits(5L, 50L, 2);

        mockMvc.perform(patch("/api/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ISSUED\", \"quantity\": 2}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk());

        verify(bnplService).issueUnits(5L, 50L, 2);
    }

    @Test
    void updateItemStatus_unknownStatus_returns4xx() throws Exception {
        // IllegalArgumentException из applyItemStatus → GlobalExceptionHandler → 400
        mockMvc.perform(patch("/api/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TELEPORTED\"}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().is4xxClientError());
    }

    // Админский путь PATCH /api/admin/orders/... тоже вызывает issueUnits.
    @Test
    void adminUpdateItemStatus_issue_callsIssueUnits() throws Exception {
        doNothing().when(bnplService).issueUnits(5L, 50L, 1);

        mockMvc.perform(patch("/api/admin/orders/5/items/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ISSUED\"}")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(bnplService).issueUnits(5L, 50L, 1);
    }
}
