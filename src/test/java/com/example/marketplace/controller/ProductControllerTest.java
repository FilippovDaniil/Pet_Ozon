package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты ProductController: публичные эндпоинты каталога.
// GET /api/products и GET /api/products/{id} доступны без аутентификации.
@WebMvcTest(
        value = ProductController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ProductService productService;

    private ProductResponse makeResponse(Long id, String name, BigDecimal price, int stock) {
        ProductResponse r = new ProductResponse();
        r.setId(id);
        r.setName(name);
        r.setPrice(price);
        r.setStockQuantity(stock);
        return r;
    }

    // ── GET /api/products ─────────────────────────────────────────────────────

    @Test
    void getAllProducts_noFilters_returns200WithPage() throws Exception {
        PageImpl<ProductResponse> page = new PageImpl<>(List.of(
                makeResponse(1L, "Ноутбук", new BigDecimal("79999.99"), 10),
                makeResponse(2L, "Мышь",    new BigDecimal("1999.99"),  50)
        ));
        // isNull() — проверяет что аргумент == null (фильтр не передан)
        // any(Pageable.class) — любой объект пагинации
        when(productService.getAllProducts(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        // Запрос без query-параметров — все фильтры null
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ноутбук"))
                .andExpect(jsonPath("$.content[1].name").value("Мышь"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getAllProducts_empty_returns200WithEmptyPage() throws Exception {
        when(productService.getAllProducts(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getAllProducts_withNameFilter_passesNameToService() throws Exception {
        PageImpl<ProductResponse> page = new PageImpl<>(
                List.of(makeResponse(1L, "Ноутбук", new BigDecimal("79999.99"), 10))
        );
        // eq("Ноутбук") — первый аргумент должен быть именно "Ноутбук", а не просто any()
        when(productService.getAllProducts(eq("Ноутбук"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        // ?name=Ноутбук — Spring извлекает @RequestParam name из URL и передаёт в сервис
        mockMvc.perform(get("/api/products?name=Ноутбук"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Ноутбук"));

        // Проверяем что сервис вызван с правильным именем (не null)
        verify(productService).getAllProducts(eq("Ноутбук"), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getAllProducts_withPriceRange_passesToService() throws Exception {
        when(productService.getAllProducts(isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // ?minPrice=1000&maxPrice=50000 — Spring автоматически конвертирует строки в BigDecimal
        mockMvc.perform(get("/api/products?minPrice=1000&maxPrice=50000"))
                .andExpect(status().isOk());

        verify(productService).getAllProducts(isNull(), isNull(), any(), any(), any(Pageable.class));
    }

    @Test
    void getAllProducts_withPagination_passesPageableToService() throws Exception {
        when(productService.getAllProducts(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // ?page=1&size=5 — Spring создаёт PageRequest.of(1, 5) из этих параметров
        mockMvc.perform(get("/api/products?page=1&size=5"))
                .andExpect(status().isOk());

        verify(productService).getAllProducts(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────

    @Test
    void getProductById_found_returns200WithProduct() throws Exception {
        when(productService.getProductById(1L))
                .thenReturn(makeResponse(1L, "Ноутбук", new BigDecimal("79999.99"), 10));

        mockMvc.perform(get("/api/products/1")) // {id} = 1 из URL (@PathVariable)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ноутбук"))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void getProductById_notFound_returns404WithErrorBody() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                // GlobalExceptionHandler возвращает ErrorResponse с полями status и message
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }
}
