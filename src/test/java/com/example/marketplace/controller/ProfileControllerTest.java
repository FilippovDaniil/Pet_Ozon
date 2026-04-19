package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ProfileController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class ProfileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setFullName("Иван Клиентов");
        u.setAddress("Москва, ул. Примерная, 1");
        u.setRole(Role.CLIENT);
        u.setBalance(new BigDecimal("500.00"));
        return u;
    }

    // ── GET /api/profile/me ───────────────────────────────────────────────────

    @Test
    void getProfile_authenticated_returns200WithUserData() throws Exception {
        mockMvc.perform(get("/api/profile/me")
                        .with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("client@example.com"))
                .andExpect(jsonPath("$.fullName").value("Иван Клиентов"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    void getProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/profile/me ─────────────────────────────────────────────────

    @Test
    void updateProfile_allFields_returns200WithUpdatedData() throws Exception {
        User updated = new User();
        updated.setId(1L);
        updated.setEmail("client@example.com");
        updated.setFullName("Иван Обновлённый");
        updated.setAddress("Санкт-Петербург, Невский пр., 1");
        updated.setRole(Role.CLIENT);
        updated.setBalance(new BigDecimal("500.00"));

        when(userService.updateProfile(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/profile/me")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Иван Обновлённый\",\"address\":\"Санкт-Петербург, Невский пр., 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Иван Обновлённый"))
                .andExpect(jsonPath("$.address").value("Санкт-Петербург, Невский пр., 1"));
    }

    @Test
    void updateProfile_onlyFullName_returns200() throws Exception {
        User updated = mockClientUser();
        updated.setFullName("Новое Имя");
        when(userService.updateProfile(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/profile/me")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Новое Имя\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Новое Имя"));
    }

    @Test
    void updateProfile_shopName_returns200() throws Exception {
        User updated = mockClientUser();
        updated.setShopName("Мой Магазин");
        when(userService.updateProfile(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/profile/me")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shopName\":\"Мой Магазин\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Мой Магазин"));
    }

    @Test
    void updateProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Тест\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_userNotFound_returns404() throws Exception {
        when(userService.updateProfile(eq(1L), any()))
                .thenThrow(new com.example.marketplace.exception.ResourceNotFoundException("User not found: 1"));

        mockMvc.perform(patch("/api/profile/me")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Тест\"}"))
                .andExpect(status().isNotFound());
    }
}
