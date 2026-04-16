package com.example.marketplace.controller;

import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.SellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SellerController.class)
class SellerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SellerService sellerService;
    @MockitoBean OrderService  orderService;

    private static final Long DEFAULT_SELLER_ID = 3L;

    private ProductResponse makeProductResponse(Long id, String name) {
        ProductResponse r = new ProductResponse();
        r.setId(id);
        r.setName(name);
        r.setPrice(new BigDecimal("1500.00"));
        r.setStockQuantity(10);
        return r;
    }

    private SellerResponse makeSellerResponse(Long id) {
        SellerResponse r = new SellerResponse();
        r.setId(id);
        r.setEmail("seller@example.com");
        r.setFullName("Test Seller");
        r.setShopName("Test Shop");
        r.setBalance(new BigDecimal("25000.00"));
        return r;
    }

    private OrderResponse makeOrderResponse(Long id, OrderStatus status) {
        OrderResponse r = new OrderResponse();
        r.setId(id);
        r.setOrderDate(LocalDateTime.now());
        r.setStatus(status);
        r.setTotalAmount(new BigDecimal("3000.00"));
        r.setShippingAddress("Москва");
        r.setItems(new ArrayList<>());
        return r;
    }

    // ── GET /api/seller/products ──────────────────────────────────────────────

    @Test
    void getMyProducts_withHeader_callsServiceWithGivenId() throws Exception {
        when(sellerService.getSellerProducts(5L)).thenReturn(
                List.of(makeProductResponse(1L, "Товар 1"), makeProductResponse(2L, "Товар 2"))
        );

        mockMvc.perform(get("/api/seller/products").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getMyProducts_withoutHeader_usesDefaultSellerId() throws Exception {
        when(sellerService.getSellerProducts(DEFAULT_SELLER_ID)).thenReturn(
                List.of(makeProductResponse(1L, "Товар"))
        );

        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(sellerService).getSellerProducts(DEFAULT_SELLER_ID);
    }

    @Test
    void getMyProducts_sellerNotFound_returns404() throws Exception {
        when(sellerService.getSellerProducts(DEFAULT_SELLER_ID))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyProducts_userIsNotSeller_returns400() throws Exception {
        when(sellerService.getSellerProducts(DEFAULT_SELLER_ID))
                .thenThrow(new IllegalArgumentException("User 3 is not a seller"));

        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/seller/products ─────────────────────────────────────────────

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        when(sellerService.createProduct(eq(DEFAULT_SELLER_ID), any()))
                .thenReturn(makeProductResponse(10L, "Новый товар"));

        mockMvc.perform(post("/api/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новый товар\",\"price\":1500.00,\"stockQuantity\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Новый товар"));
    }

    @Test
    void createProduct_sellerNotFound_returns404() throws Exception {
        when(sellerService.createProduct(eq(DEFAULT_SELLER_ID), any()))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(post("/api/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Товар\",\"price\":100.00,\"stockQuantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_userIsNotSeller_returns400() throws Exception {
        when(sellerService.createProduct(eq(DEFAULT_SELLER_ID), any()))
                .thenThrow(new IllegalArgumentException("User 3 is not a seller"));

        mockMvc.perform(post("/api/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Товар\",\"price\":100.00,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/seller/products/{id} ─────────────────────────────────────────

    @Test
    void updateProduct_found_returns200WithUpdatedData() throws Exception {
        when(sellerService.updateProduct(eq(DEFAULT_SELLER_ID), eq(1L), any()))
                .thenReturn(makeProductResponse(1L, "Обновлённый товар"));

        mockMvc.perform(put("/api/seller/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Обновлённый товар\",\"price\":2000.00,\"stockQuantity\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Обновлённый товар"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        when(sellerService.updateProduct(eq(DEFAULT_SELLER_ID), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(put("/api/seller/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Нет\",\"price\":1.0,\"stockQuantity\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_belongsToAnotherSeller_returns400() throws Exception {
        when(sellerService.updateProduct(eq(DEFAULT_SELLER_ID), eq(2L), any()))
                .thenThrow(new IllegalArgumentException("Product does not belong to this seller"));

        mockMvc.perform(put("/api/seller/products/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Чужой\",\"price\":1.0,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product does not belong to this seller"));
    }

    // ── DELETE /api/seller/products/{id} ──────────────────────────────────────

    @Test
    void deleteProduct_found_returns204() throws Exception {
        doNothing().when(sellerService).deleteProduct(DEFAULT_SELLER_ID, 1L);

        mockMvc.perform(delete("/api/seller/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99"))
                .when(sellerService).deleteProduct(DEFAULT_SELLER_ID, 99L);

        mockMvc.perform(delete("/api/seller/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void deleteProduct_belongsToAnotherSeller_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Product does not belong to this seller"))
                .when(sellerService).deleteProduct(DEFAULT_SELLER_ID, 5L);

        mockMvc.perform(delete("/api/seller/products/5"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/seller/balance ───────────────────────────────────────────────

    @Test
    void getBalance_returns200WithSellerInfo() throws Exception {
        when(sellerService.getBalance(DEFAULT_SELLER_ID)).thenReturn(makeSellerResponse(DEFAULT_SELLER_ID));

        mockMvc.perform(get("/api/seller/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.shopName").value("Test Shop"))
                .andExpect(jsonPath("$.balance").value(25000.00));
    }

    @Test
    void getBalance_sellerNotFound_returns404() throws Exception {
        when(sellerService.getBalance(DEFAULT_SELLER_ID))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/balance"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/seller/sales ─────────────────────────────────────────────────

    @Test
    void getSales_returnsMappedOrderList() throws Exception {
        Order order = new Order();
        order.setId(1L);
        when(sellerService.getSellerOrders(DEFAULT_SELLER_ID)).thenReturn(List.of(order));
        when(orderService.toResponse(order)).thenReturn(makeOrderResponse(1L, OrderStatus.PAID));

        mockMvc.perform(get("/api/seller/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void getSales_noOrders_returnsEmptyList() throws Exception {
        when(sellerService.getSellerOrders(DEFAULT_SELLER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/seller/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSales_sellerNotFound_returns404() throws Exception {
        when(sellerService.getSellerOrders(DEFAULT_SELLER_ID))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/sales"))
                .andExpect(status().isNotFound());
    }
}
