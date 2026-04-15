package com.example.marketplace.controller;

import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean  ProductService productService;

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
    void getAllProducts_returns200WithList() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(
                makeResponse(1L, "Ноутбук", new BigDecimal("79999.99"), 10),
                makeResponse(2L, "Мышь",    new BigDecimal("1999.99"),  50)
        ));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Ноутбук"))
                .andExpect(jsonPath("$[1].name").value("Мышь"));
    }

    @Test
    void getAllProducts_empty_returns200WithEmptyArray() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────

    @Test
    void getProductById_found_returns200WithProduct() throws Exception {
        when(productService.getProductById(1L))
                .thenReturn(makeResponse(1L, "Ноутбук", new BigDecimal("79999.99"), 10));

        mockMvc.perform(get("/api/products/1"))
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
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }
}
