package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты OrderController: GET /api/orders/my (с пагинацией), GET /api/orders/{id}.
@WebMvcTest(
        value = OrderController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean OrderService orderService;

    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

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
    void getMyOrders_authenticated_returns200WithPage() throws Exception {
        // PageImpl — реализация Page<T> для тестов: оборачивает обычный список
        PageImpl<OrderResponse> page = new PageImpl<>(List.of(
                makeOrderResponse(1L, OrderStatus.CREATED, new BigDecimal("5000.00")),
                makeOrderResponse(2L, OrderStatus.PAID, new BigDecimal("3000.00"))
        ));
        // eq(1L) — первый аргумент должен быть ровно 1L (id из аутентификации)
        // any(Pageable.class) — параметры пагинации могут быть любые
        when(orderService.getOrdersByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/orders/my").with(user(mockClientUser())))
                .andExpect(status().isOk())
                // $.content — массив элементов страницы в ответе Page<T>
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.content[1].status").value("PAID"))
                // $.totalElements — общее число элементов (не только на этой странице)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getMyOrders_noOrders_returns200WithEmptyPage() throws Exception {
        when(orderService.getOrdersByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/orders/my").with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getMyOrders_unauthenticated_returns401() throws Exception {
        // Запрос без токена/аутентификации → 401 Unauthorized
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyOrders_userNotFound_returns404() throws Exception {
        when(orderService.getOrdersByUserId(eq(1L), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 1"));

        mockMvc.perform(get("/api/orders/my").with(user(mockClientUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getMyOrders_withPaginationParams_passes() throws Exception {
        // ?page=0&size=5 — Spring автоматически создаёт Pageable из query-параметров
        when(orderService.getOrdersByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/orders/my?page=0&size=5").with(user(mockClientUser())))
                .andExpect(status().isOk());

        // Убеждаемся что сервис был вызван (значит параметры пагинации дошли до контроллера)
        verify(orderService).getOrdersByUserId(eq(1L), any(Pageable.class));
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Test
    void getOrderById_found_returns200() throws Exception {
        when(orderService.getOrderById(1L))
                .thenReturn(makeOrderResponse(1L, OrderStatus.PAID, new BigDecimal("7500.00")));

        mockMvc.perform(get("/api/orders/1").with(user(mockClientUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(7500.00));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/orders/99").with(user(mockClientUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }

    @Test
    void getOrderById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());
    }
}
