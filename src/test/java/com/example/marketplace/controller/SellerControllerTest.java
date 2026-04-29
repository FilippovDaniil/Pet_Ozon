package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.SellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

// Тесты SellerController: управление товарами продавца, баланс, продажи.
// Все эндпоинты /api/seller/** требуют роли SELLER — тестируем и авторизацию.
@WebMvcTest(
        value = SellerController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class SellerControllerTest {

    @Autowired MockMvc mockMvc;

    // SellerController использует два сервиса
    @MockitoBean SellerService sellerService;
    @MockitoBean OrderService  orderService;

    // Создаёт пользователя с ролью SELLER и заданным id
    private User mockSellerUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("seller@example.com");
        u.setShopName("Test Shop");
        u.setRole(Role.SELLER);
        return u;
    }

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
    void getMyProducts_authenticated_returnsSellerProducts() throws Exception {
        // PageImpl — реализация Page с content + метаданными (totalElements, totalPages и т.д.).
        // Используем any(Pageable.class): контроллер сам создаёт Pageable из параметров запроса,
        // и мы не знаем точный объект — поэтому матчим любой Pageable.
        Page<ProductResponse> page = new PageImpl<>(
                List.of(makeProductResponse(1L, "Товар 1"), makeProductResponse(2L, "Товар 2"))
        );
        when(sellerService.getSellerProducts(eq(3L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/seller/products").with(user(mockSellerUser(3L))))
                .andExpect(status().isOk())
                // Page<T> сериализуется в JSON: { content: [...], totalElements: N, ... }
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].id").value(2));
    }

    @Test
    void getMyProducts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProducts_clientRole_returns403() throws Exception {
        // CLIENT не имеет роли SELLER → .requestMatchers("/api/seller/**").hasRole("SELLER") → 403
        User client = new User();
        client.setId(1L);
        client.setRole(Role.CLIENT);

        mockMvc.perform(get("/api/seller/products").with(user(client)))
                .andExpect(status().isForbidden()); // HTTP 403 Forbidden
    }

    @Test
    void getMyProducts_sellerNotFound_returns404() throws Exception {
        when(sellerService.getSellerProducts(eq(3L), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/products").with(user(mockSellerUser(3L))))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/seller/products ─────────────────────────────────────────────

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        // eq(3L) — первый аргумент (sellerId) должен быть 3L
        // any() — второй аргумент (CreateProductRequest) может быть любым объектом
        when(sellerService.createProduct(eq(3L), any()))
                .thenReturn(makeProductResponse(10L, "Новый товар"));

        mockMvc.perform(post("/api/seller/products")
                        .with(user(mockSellerUser(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новый товар\",\"price\":1500.00,\"stockQuantity\":5}"))
                .andExpect(status().isCreated()) // HTTP 201 Created
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Новый товар"));
    }

    @Test
    void createProduct_sellerNotFound_returns404() throws Exception {
        when(sellerService.createProduct(eq(3L), any()))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(post("/api/seller/products")
                        .with(user(mockSellerUser(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Товар\",\"price\":100.00,\"stockQuantity\":1}"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/seller/products/{id} ─────────────────────────────────────────

    @Test
    void updateProduct_found_returns200WithUpdatedData() throws Exception {
        // sellerId=3L, productId=1L, req=any()
        when(sellerService.updateProduct(eq(3L), eq(1L), any()))
                .thenReturn(makeProductResponse(1L, "Обновлённый товар"));

        mockMvc.perform(put("/api/seller/products/1") // {id}=1 в URL — это productId
                        .with(user(mockSellerUser(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Обновлённый товар\",\"price\":2000.00,\"stockQuantity\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Обновлённый товар"));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        when(sellerService.updateProduct(eq(3L), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(put("/api/seller/products/99")
                        .with(user(mockSellerUser(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Нет\",\"price\":1.0,\"stockQuantity\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_belongsToAnotherSeller_returns400() throws Exception {
        // Продавец пытается обновить чужой товар → IllegalArgumentException → 400
        when(sellerService.updateProduct(eq(3L), eq(2L), any()))
                .thenThrow(new IllegalArgumentException("Product does not belong to this seller"));

        mockMvc.perform(put("/api/seller/products/2")
                        .with(user(mockSellerUser(3L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Чужой\",\"price\":1.0,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product does not belong to this seller"));
    }

    // ── DELETE /api/seller/products/{id} ──────────────────────────────────────

    @Test
    void deleteProduct_found_returns204() throws Exception {
        doNothing().when(sellerService).deleteProduct(3L, 1L);

        mockMvc.perform(delete("/api/seller/products/1")
                        .with(user(mockSellerUser(3L))))
                .andExpect(status().isNoContent()); // HTTP 204 No Content (успешное удаление без тела)
    }

    @Test
    void deleteProduct_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99"))
                .when(sellerService).deleteProduct(3L, 99L);

        mockMvc.perform(delete("/api/seller/products/99")
                        .with(user(mockSellerUser(3L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void deleteProduct_belongsToAnotherSeller_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Product does not belong to this seller"))
                .when(sellerService).deleteProduct(3L, 5L);

        mockMvc.perform(delete("/api/seller/products/5")
                        .with(user(mockSellerUser(3L))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/seller/balance ───────────────────────────────────────────────

    @Test
    void getBalance_returns200WithSellerInfo() throws Exception {
        when(sellerService.getBalance(3L)).thenReturn(makeSellerResponse(3L));

        mockMvc.perform(get("/api/seller/balance").with(user(mockSellerUser(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.shopName").value("Test Shop"))
                .andExpect(jsonPath("$.balance").value(25000.00));
    }

    @Test
    void getBalance_sellerNotFound_returns404() throws Exception {
        when(sellerService.getBalance(3L))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/balance").with(user(mockSellerUser(3L))))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/seller/sales ─────────────────────────────────────────────────

    @Test
    void getSales_returnsMappedOrderList() throws Exception {
        // getSellerOrders возвращает Entity-объекты Order
        Order order = new Order();
        order.setId(1L);
        when(sellerService.getSellerOrders(3L)).thenReturn(List.of(order));
        // Контроллер вызывает orderService.toResponse() для преобразования Order → OrderResponse
        when(orderService.toResponse(order)).thenReturn(makeOrderResponse(1L, OrderStatus.PAID));

        mockMvc.perform(get("/api/seller/sales").with(user(mockSellerUser(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void getSales_noOrders_returnsEmptyList() throws Exception {
        when(sellerService.getSellerOrders(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/seller/sales").with(user(mockSellerUser(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSales_sellerNotFound_returns404() throws Exception {
        when(sellerService.getSellerOrders(3L))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 3"));

        mockMvc.perform(get("/api/seller/sales").with(user(mockSellerUser(3L))))
                .andExpect(status().isNotFound());
    }
}
