package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.AuthResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.security.JwtUtil;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты AuthController: вход (login) и регистрация (register).
// Эндпоинты /api/auth/** публичные — не требуют токена, поэтому .with(user(...)) не нужен.
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

    // AuthController использует четыре зависимости — все мокируем
    @MockitoBean UserService userService;
    @MockitoBean JwtUtil jwtUtil;
    // AuthenticationManager — компонент Spring Security, проверяет email + password
    @MockitoBean AuthenticationManager authenticationManager;
    // UserDetailsService — загружает UserDetails (наш User) по username (email)
    @MockitoBean UserDetailsService userDetailsService;

    // Тестовый пользователь-клиент для имитации успешного логина
    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setPassword("encoded");  // хешированный пароль (в реальности BCrypt)
        u.setFullName("Иван Клиентов");
        u.setRole(Role.CLIENT);
        u.setBalance(BigDecimal.ZERO);
        return u;
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        User client = mockClientUser();
        // authenticate(any()) → null: Spring Security успешно проверил пароль (без броска исключения)
        when(authenticationManager.authenticate(any())).thenReturn(null);
        // loadUserByUsername возвращает нашего пользователя (он реализует UserDetails)
        when(userDetailsService.loadUserByUsername("client@example.com")).thenReturn(client);
        // generateToken — мок возвращает фиктивный токен вместо реального JWT
        when(jwtUtil.generateToken(client)).thenReturn("jwt-token-123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))   // токен в теле ответа
                .andExpect(jsonPath("$.email").value("client@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(header().exists("Authorization")); // токен также в заголовке
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        // BadCredentialsException — стандартное исключение Spring Security при неверном пароле
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()); // GlobalExceptionHandler → 401
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        // @Valid + @Email: пустой email не проходит валидацию → MethodArgumentNotValidException → 400
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        // @Email проверяет формат: "not-an-email" не является корректным email-адресом
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        // @NotBlank: пустой пароль не проходит валидацию
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"client@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        User newUser = new User();
        newUser.setId(10L);
        newUser.setEmail("newuser@example.com");
        newUser.setFullName("Новый Пользователь");
        newUser.setRole(Role.CLIENT);
        newUser.setBalance(BigDecimal.ZERO);

        when(userService.registerClient("newuser@example.com", "password123", "Новый Пользователь"))
                .thenReturn(newUser);
        when(jwtUtil.generateToken(newUser)).thenReturn("new-jwt-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"password123\",\"fullName\":\"Новый Пользователь\"}"))
                .andExpect(status().isCreated()) // HTTP 201 Created (не 200 OK)
                .andExpect(jsonPath("$.token").value("new-jwt-token"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        // registerClient бросает IllegalArgumentException если email уже занят
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
        // @Size(min = 6): пароль "123" — слишком короткий → 400
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
}
