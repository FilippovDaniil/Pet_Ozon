package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.CartItemResponse;
import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.CartService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = CartController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class CartControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CartService cartService;

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private CartResponse emptyCartResponse() {
        CartResponse r = new CartResponse();
        r.setId(1L);
        r.setItems(new ArrayList<>());
        r.setTotalPrice(BigDecimal.ZERO);
        return r;
    }

    private CartResponse cartWithOneItem() {
        CartItemResponse item = new CartItemResponse();
        item.setId(1L);
        item.setProductId(1L);
        item.setProductName("Ноутбук");
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50000.00"));

        CartResponse r = new CartResponse();
        r.setId(1L);
        r.setItems(List.of(item));
        r.setTotalPrice(new BigDecimal("100000.00"));
        return r;
    }

    private OrderResponse makeOrderResponse() {
        OrderResponse r = new OrderResponse();
        r.setId(1L);
        r.setOrderDate(LocalDateTime.now());
        r.setStatus(OrderStatus.CREATED);
        r.setTotalAmount(new BigDecimal("100000.00"));
        r.setShippingAddress("Москва");
        r.setItems(new ArrayList<>());
        return r;
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Test
    void getCart_authenticated_returns200() throws Exception {
        when(cartService.getCartByUserId(1L)).thenReturn(cartWithOneItem());

        mockMvc.perform(get("/api/cart").with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPrice").value(100000.00));
    }

    @Test
    void getCart_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCart_userNotFound_returns404() throws Exception {
        when(cartService.getCartByUserId(1L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 1"));

        mockMvc.perform(get("/api/cart").with(user(mockClientUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── POST /api/cart/add ────────────────────────────────────────────────────

    @Test
    void addToCart_validRequest_returns200() throws Exception {
        when(cartService.addToCart(1L, 1L, 2)).thenReturn(cartWithOneItem());

        mockMvc.perform(post("/api/cart/add")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void addToCart_productNotFound_returns404() throws Exception {
        when(cartService.addToCart(1L, 99L, 1))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/cart/add")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 99, \"quantity\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void addToCart_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"quantity\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/cart/update/{cartItemId} ─────────────────────────────────────

    @Test
    void updateCartItem_validQuantity_returns200() throws Exception {
        when(cartService.updateQuantity(1L, 5)).thenReturn(emptyCartResponse());

        mockMvc.perform(put("/api/cart/update/1")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateCartItem_zeroQuantity_returns400() throws Exception {
        when(cartService.updateQuantity(1L, 0))
                .thenThrow(new IllegalArgumentException("Quantity must be greater than 0"));

        mockMvc.perform(put("/api/cart/update/1")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── DELETE /api/cart/remove/{cartItemId} ──────────────────────────────────

    @Test
    void removeFromCart_found_returns200() throws Exception {
        doNothing().when(cartService).removeFromCart(1L);

        mockMvc.perform(delete("/api/cart/remove/1")
                        .with(user(mockClientUser())))
                .andExpect(status().isOk());
    }

    @Test
    void removeFromCart_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("CartItem not found with id: 99"))
                .when(cartService).removeFromCart(99L);

        mockMvc.perform(delete("/api/cart/remove/99")
                        .with(user(mockClientUser())))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/cart/checkout ───────────────────────────────────────────────

    @Test
    void checkout_success_returns200WithOrder() throws Exception {
        when(cartService.checkout(1L, "Москва, ул. Тестовая, 1"))
                .thenReturn(makeOrderResponse());

        mockMvc.perform(post("/api/cart/checkout")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\": \"Москва, ул. Тестовая, 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void checkout_emptyCart_returns400() throws Exception {
        when(cartService.checkout(1L, "Москва"))
                .thenThrow(new IllegalArgumentException("Cart is empty"));

        mockMvc.perform(post("/api/cart/checkout")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\": \"Москва\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void checkout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\": \"Москва\"}"))
                .andExpect(status().isUnauthorized());
    }
}
