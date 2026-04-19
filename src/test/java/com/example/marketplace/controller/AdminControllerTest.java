package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.InvoiceService;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ProductService productService;
    @MockitoBean OrderService   orderService;
    @MockitoBean InvoiceService invoiceService;

    private User mockAdminUser() {
        User u = new User();
        u.setId(2L);
        u.setEmail("admin@example.com");
        u.setRole(Role.ADMIN);
        return u;
    }

    private ProductResponse makeProductResponse(Long id, String name) {
        ProductResponse r = new ProductResponse();
        r.setId(id);
        r.setName(name);
        r.setPrice(new BigDecimal("9999.99"));
        r.setStockQuantity(10);
        return r;
    }

    private OrderResponse makeOrderResponse(Long id, OrderStatus status) {
        OrderResponse r = new OrderResponse();
        r.setId(id);
        r.setOrderDate(LocalDateTime.now());
        r.setStatus(status);
        r.setTotalAmount(new BigDecimal("5000.00"));
        r.setShippingAddress("Москва");
        r.setItems(new ArrayList<>());
        return r;
    }

    private InvoiceResponse makeInvoiceResponse(Long id, InvoiceStatus status) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(id);
        r.setOrderId(1L);
        r.setAmount(new BigDecimal("5000.00"));
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    // ── POST /api/admin/products ──────────────────────────────────────────────

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        when(productService.createProduct(any())).thenReturn(makeProductResponse(6L, "Планшет"));

        mockMvc.perform(post("/api/admin/products")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Планшет\",\"price\":29999.99,\"stockQuantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.name").value("Планшет"));
    }

    @Test
    void createProduct_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Планшет\",\"price\":29999.99,\"stockQuantity\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_clientRole_returns403() throws Exception {
        User client = new User();
        client.setId(1L);
        client.setRole(Role.CLIENT);

        mockMvc.perform(post("/api/admin/products")
                        .with(user(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Планшет\",\"price\":29999.99,\"stockQuantity\":10}"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/products/{id} ──────────────────────────────────────────

    @Test
    void updateProduct_found_returns200() throws Exception {
        when(productService.updateProduct(eq(1L), any()))
                .thenReturn(makeProductResponse(1L, "Ноутбук (обновлён)"));

        mockMvc.perform(put("/api/admin/products/1")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ноутбук (обновлён)\",\"price\":89999.99,\"stockQuantity\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ноутбук (обновлён)"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        when(productService.updateProduct(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(put("/api/admin/products/99")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Нет\",\"price\":1.0,\"stockQuantity\":0}"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/admin/products/{id} ───────────────────────────────────────

    @Test
    void deleteProduct_found_returns204() throws Exception {
        doNothing().when(productService).deleteProduct(6L);

        mockMvc.perform(delete("/api/admin/products/6")
                        .with(user(mockAdminUser())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99"))
                .when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/admin/products/99")
                        .with(user(mockAdminUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────

    @Test
    void getAllOrders_returns200WithPage() throws Exception {
        PageImpl<OrderResponse> page = new PageImpl<>(List.of(
                makeOrderResponse(1L, OrderStatus.CREATED),
                makeOrderResponse(2L, OrderStatus.PAID)
        ));
        when(orderService.getAllOrders(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/orders")
                        .with(user(mockAdminUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.content[1].status").value("PAID"));
    }

    @Test
    void getAllOrders_withPagination_passesPageableToService() throws Exception {
        when(orderService.getAllOrders(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/orders?page=0&size=10")
                        .with(user(mockAdminUser())))
                .andExpect(status().isOk());

        verify(orderService).getAllOrders(any(Pageable.class));
    }

    // ── PUT /api/admin/orders/{id}/status ─────────────────────────────────────

    @Test
    void updateOrderStatus_delivered_returns200() throws Exception {
        OrderResponse updated = makeOrderResponse(1L, OrderStatus.DELIVERED);
        when(orderService.updateStatus(eq(1L), eq(OrderStatus.DELIVERED))).thenReturn(updated);

        mockMvc.perform(put("/api/admin/orders/1/status")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void updateOrderStatus_cancelled_returns200() throws Exception {
        OrderResponse updated = makeOrderResponse(1L, OrderStatus.CANCELLED);
        when(orderService.updateStatus(eq(1L), eq(OrderStatus.CANCELLED))).thenReturn(updated);

        mockMvc.perform(put("/api/admin/orders/1/status")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void updateOrderStatus_notFound_returns404() throws Exception {
        when(orderService.updateStatus(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(put("/api/admin/orders/99/status")
                        .with(user(mockAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/admin/invoices ───────────────────────────────────────────────

    @Test
    void getAllInvoices_returns200WithList() throws Exception {
        when(invoiceService.getAllInvoices()).thenReturn(List.of(
                makeInvoiceResponse(1L, InvoiceStatus.UNPAID),
                makeInvoiceResponse(2L, InvoiceStatus.PAID)
        ));

        mockMvc.perform(get("/api/admin/invoices")
                        .with(user(mockAdminUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("UNPAID"))
                .andExpect(jsonPath("$[1].status").value("PAID"));
    }

    @Test
    void getAllInvoices_empty_returns200WithEmptyArray() throws Exception {
        when(invoiceService.getAllInvoices()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/invoices")
                        .with(user(mockAdminUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
