package com.example.marketplace.controller;

import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean  OrderService orderService;

    private OrderResponse makeOrderResponse(Long id, OrderStatus status, BigDecimal total) {
        OrderResponse r = new OrderResponse();
        r.setId(id);
        r.setOrderDate(LocalDateTime.now());
        r.setStatus(status);
        r.setTotalAmount(total);
        r.setShippingAddress("Москва");
        r.setItems(new ArrayList<>());
        return r;
    }

    // ── GET /api/orders/my ────────────────────────────────────────────────────

    @Test
    void getMyOrders_withHeader_returns200WithList() throws Exception {
        when(orderService.getOrdersByUserId(1L)).thenReturn(List.of(
                makeOrderResponse(1L, OrderStatus.CREATED, new BigDecimal("5000.00")),
                makeOrderResponse(2L, OrderStatus.PAID,    new BigDecimal("3000.00"))
        ));

        mockMvc.perform(get("/api/orders/my").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[1].status").value("PAID"));
    }

    @Test
    void getMyOrders_withoutHeader_usesDefaultUser1() throws Exception {
        when(orderService.getOrdersByUserId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isOk());

        verify(orderService).getOrdersByUserId(1L);
    }

    @Test
    void getMyOrders_noOrders_returns200WithEmptyArray() throws Exception {
        when(orderService.getOrdersByUserId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/my").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMyOrders_userNotFound_returns404() throws Exception {
        when(orderService.getOrdersByUserId(99L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/orders/my").header("X-User-Id", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Test
    void getOrderById_found_returns200() throws Exception {
        when(orderService.getOrderById(1L))
                .thenReturn(makeOrderResponse(1L, OrderStatus.PAID, new BigDecimal("7500.00")));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(7500.00));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }
}
