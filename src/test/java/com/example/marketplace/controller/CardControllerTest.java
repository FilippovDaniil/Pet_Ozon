package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.CardBindingResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.UserRepository;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты CardController: список карт, смена дефолтной, удаление.
 * @WebMvcTest со срезом web-слоя; SecurityConfig и JWT-фильтр заменены TestSecurityConfig,
 * аутентификация подставляется через .with(user(...)).
 */
@WebMvcTest(
        value = CardController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class CardControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CardService cardService;
    @MockitoBean UserRepository userRepository;

    private User mockUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@test.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private CardBindingResponse makeCardResponse(Long id, String pan, boolean isDefault) {
        return new CardBindingResponse(id, pan, "12/2026", isDefault, LocalDateTime.now().toString());
    }

    // ── GET /api/cards ────────────────────────────────────────────────────────

    // Авторизованный клиент получает список своих карт (дефолтная — первой).
    @Test
    void getCards_authenticated_returnsCardList() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(cardService.getCards(user)).thenReturn(List.of(
                makeCardResponse(1L, "411111**1111", true),
                makeCardResponse(2L, "555555**4444", false)
        ));

        mockMvc.perform(get("/api/cards").with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].maskedPan").value("555555**4444"));
    }

    // Без аутентификации — 401.
    @Test
    void getCards_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
    }

    // Нет карт — пустой JSON-массив (не ошибка).
    @Test
    void getCards_emptyList_returnsEmptyArray() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(cardService.getCards(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/cards").with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── PATCH /api/cards/{id}/default ─────────────────────────────────────────

    // Смена дефолтной своей карты → 200, вызов делегируется сервису.
    @Test
    void setDefault_ownCard_returns200() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        doNothing().when(cardService).setDefault(5L, user);

        mockMvc.perform(patch("/api/cards/5/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk());

        verify(cardService).setDefault(5L, user);
    }

    // Карта не найдена → ResourceNotFoundException → 404 (имя метода историческое, фактически 404).
    @Test
    void setDefault_cardNotFound_returns500() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        doThrow(new ResourceNotFoundException("Карта не найдена")).when(cardService).setDefault(99L, user);

        mockMvc.perform(patch("/api/cards/99/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/cards/{id} ────────────────────────────────────────────────

    // Удаление своей карты → 204 No Content.
    @Test
    void deleteCard_ownCard_returns204() throws Exception {
        User user = mockUser();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        doNothing().when(cardService).delete(3L, user);

        mockMvc.perform(delete("/api/cards/3")
                        .with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isNoContent());
    }

    // Удаление без аутентификации — 401.
    @Test
    void deleteCard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/cards/3"))
                .andExpect(status().isUnauthorized());
    }
}
