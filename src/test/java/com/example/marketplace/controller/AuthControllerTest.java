package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.entity.RefreshToken;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.security.JwtUtil;
import com.example.marketplace.service.RefreshTokenService;
import com.example.marketplace.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты AuthController: login, register, refresh, logout.
// /api/auth/** — публичные эндпоинты, .with(user(...)) не нужен.
@WebMvcTest(
        value = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean UserDetailsService userDetailsService;
    // RefreshTokenService добавлен в AuthController при реализации refresh-токенов.
    @MockitoBean RefreshTokenService refreshTokenService;

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setPassword("encoded");
        u.setFullName("Иван Клиентов");
        u.setRole(Role.CLIENT);
        u.setBalance(BigDecimal.ZERO);
        return u;
    }

    private RefreshToken mockRefreshToken(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setToken("refresh-uuid-123");
        rt.setUser(user);
        rt.setExpiresAt(Instant.now().plusSeconds(604_800));
        return rt;
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithBothTokens() throws Exception {
        User client = mockClientUser();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userDetailsService.loadUserByUsername("client@example.com")).thenReturn(client);
        when(jwtUtil.generateToken(client)).thenReturn("access-jwt-123");
        when(refreshTokenService.createRefreshToken(client)).thenReturn(mockRefreshToken(client));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-jwt-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid-123"))
                .andExpect(jsonPath("$.email").value("client@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(header().exists("Authorization"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithBothTokens() throws Exception {
        User newUser = new User();
        newUser.setId(10L);
        newUser.setEmail("newuser@example.com");
        newUser.setFullName("Новый Пользователь");
        newUser.setRole(Role.CLIENT);
        newUser.setBalance(BigDecimal.ZERO);

        when(userService.registerClient("newuser@example.com", "password123", "Новый Пользователь"))
                .thenReturn(newUser);
        when(jwtUtil.generateToken(newUser)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(newUser)).thenReturn(mockRefreshToken(newUser));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"password123\",\"fullName\":\"Новый Пользователь\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid-123"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        when(userService.registerClient(eq("client@example.com"), any(), any()))
                .thenThrow(new IllegalArgumentException("Email already registered: client@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered: client@example.com"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void register_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    void refresh_validToken_returnsNewTokenPair() throws Exception {
        User client = mockClientUser();
        RefreshToken oldRefresh = mockRefreshToken(client);

        when(refreshTokenService.findValid("refresh-uuid-123")).thenReturn(Optional.of(oldRefresh));
        doNothing().when(refreshTokenService).delete(oldRefresh);
        when(jwtUtil.generateToken(client)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(client)).thenReturn(mockRefreshToken(client));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-uuid-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid-123"));

        // Старый токен должен быть удалён (ротация).
        verify(refreshTokenService).delete(oldRefresh);
        // Новый refresh создан.
        verify(refreshTokenService).createRefreshToken(client);
    }

    @Test
    void refresh_expiredToken_returns500() throws Exception {
        // findValid возвращает empty когда токен истёк или не найден.
        when(refreshTokenService.findValid("expired-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-token\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void refresh_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_validToken_returns204AndDeletesToken() throws Exception {
        User client = mockClientUser();
        RefreshToken refreshToken = mockRefreshToken(client);

        when(refreshTokenService.findValid("refresh-uuid-123")).thenReturn(Optional.of(refreshToken));
        doNothing().when(refreshTokenService).delete(refreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-uuid-123\"}"))
                .andExpect(status().isNoContent());

        // Токен инвалидирован на сервере.
        verify(refreshTokenService).delete(refreshToken);
    }

    @Test
    void logout_unknownToken_returns204Silently() throws Exception {
        // Логаут с неизвестным токеном не должен возвращать ошибку.
        when(refreshTokenService.findValid("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"unknown-token\"}"))
                .andExpect(status().isNoContent());

        // delete не вызывается, если токен не найден.
        verify(refreshTokenService, never()).delete(any());
    }

    @Test
    void logout_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
