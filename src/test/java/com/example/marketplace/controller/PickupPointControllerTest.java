package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.PickupPointResponse;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.PickupPointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты PickupPointController: публичный список активных точек + админ-CRUD.
 */
@WebMvcTest(
        value = PickupPointController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class PickupPointControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PickupPointService pickupPointService;

    private PickupPointResponse pp(long id, String name) {
        return new PickupPointResponse(id, name, "ул. Тестовая, 1", "Курская", true);
    }

    // Активные точки доступны авторизованному клиенту.
    @Test
    void listActive_authenticated_returnsArray() throws Exception {
        when(pickupPointService.listActive()).thenReturn(List.of(pp(1L, "ТЦ Атриум"), pp(2L, "ГУМ")));

        mockMvc.perform(get("/api/pickup-points").with(user("client@test.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ТЦ Атриум"));
    }

    // Создание точки админом → 201.
    @Test
    void create_admin_returns201() throws Exception {
        when(pickupPointService.create(any())).thenReturn(pp(10L, "Новая точка"));

        mockMvc.perform(post("/api/admin/pickup-points")
                        .with(user("admin@test.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новая точка\",\"address\":\"ул. Новая, 5\",\"metro\":\"Курская\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Новая точка"));
    }

    // Пустое название → 400 (Bean Validation).
    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/pickup-points")
                        .with(user("admin@test.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"address\":\"ул. Новая, 5\"}"))
                .andExpect(status().isBadRequest());
    }

    // Обновление точки админом → 200.
    @Test
    void update_admin_returns200() throws Exception {
        when(pickupPointService.update(eq(3L), any())).thenReturn(pp(3L, "Обновлённая"));

        mockMvc.perform(put("/api/admin/pickup-points/3")
                        .with(user("admin@test.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Обновлённая\",\"address\":\"ул. Новая, 5\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Обновлённая"));
    }

    // Удаление точки админом → 204.
    @Test
    void delete_admin_returns204() throws Exception {
        doNothing().when(pickupPointService).delete(7L);

        mockMvc.perform(delete("/api/admin/pickup-points/7")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        verify(pickupPointService).delete(7L);
    }
}
