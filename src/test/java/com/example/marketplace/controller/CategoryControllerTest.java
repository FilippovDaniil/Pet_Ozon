package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.entity.Category;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.CategoryService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты CategoryController: CRUD операции с категориями.
// GET /api/categories  — публичный.
// POST/DELETE          — только ADMIN.
@WebMvcTest(
        value = CategoryController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CategoryService categoryService;

    private Category makeCategory(Long id, String name) {
        Category c = new Category(name);
        try {
            var f = Category.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    // ── GET /api/categories ───────────────────────────────────────────────────

    @Test
    void getAll_noAuth_returns200WithList() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(
                makeCategory(1L, "Аудио"),
                makeCategory(2L, "Ноутбуки")
        ));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Аудио"))
                .andExpect(jsonPath("$[1].name").value("Ноутбуки"));
    }

    @Test
    void getAll_emptyList_returns200EmptyArray() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── POST /api/categories ──────────────────────────────────────────────────

    @Test
    void create_asAdmin_returns201WithCategory() throws Exception {
        Category created = makeCategory(5L, "Умный дом");
        when(categoryService.create("Умный дом")).thenReturn(created);

        mockMvc.perform(post("/api/categories")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Умный дом\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Умный дом"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Сервис не должен вызываться при пустом имени.
        verify(categoryService, never()).create(any());
    }

    @Test
    void create_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новая\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asClient_returns403() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(user("client@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новая\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_duplicateName_returns400() throws Exception {
        when(categoryService.create("Аудио"))
                .thenThrow(new IllegalArgumentException("Категория уже существует: Аудио"));

        mockMvc.perform(post("/api/categories")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Аудио\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Категория уже существует: Аудио"));
    }

    // ── DELETE /api/categories/{id} ───────────────────────────────────────────

    @Test
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/categories/1")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Категория не найдена: 99"))
                .when(categoryService).delete(99L);

        mockMvc.perform(delete("/api/categories/99")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Категория не найдена: 99"));
    }

    @Test
    void delete_withoutAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_asClient_returns403() throws Exception {
        mockMvc.perform(delete("/api/categories/1")
                        .with(user("client@example.com").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }
}
