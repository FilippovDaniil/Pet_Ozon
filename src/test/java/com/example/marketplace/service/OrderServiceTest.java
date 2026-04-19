package com.example.marketplace.service;

import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.InvoiceRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository   orderRepository;
    @Mock UserRepository    userRepository;
    @Mock InvoiceRepository invoiceRepository;

    @InjectMocks
    OrderService orderService;

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user@test.com");
        return u;
    }

    private Order makeOrder(Long id, User user, OrderStatus status, BigDecimal total) {
        Order o = new Order();
        o.setId(id);
        o.setUser(user);
        o.setStatus(status);
        o.setTotalAmount(total);
        o.setShippingAddress("Москва");
        o.setOrderDate(LocalDateTime.now());
        o.setItems(new ArrayList<>());
        return o;
    }

    // ── getAllOrders ──────────────────────────────────────────────────────────

    @Test
    void getAllOrders_returnsPage() {
        User user = makeUser(1L);
        PageImpl<Order> orderPage = new PageImpl<>(List.of(
                makeOrder(1L, user, OrderStatus.CREATED, new BigDecimal("5000.00")),
                makeOrder(2L, user, OrderStatus.PAID,    new BigDecimal("3000.00"))
        ));
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orderPage);

        Page<OrderResponse> result = orderService.getAllOrders(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getContent().get(1).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void getAllOrders_empty_returnsEmptyPage() {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(orderService.getAllOrders(Pageable.unpaged()).getContent()).isEmpty();
    }

    // ── getOrdersByUserId ─────────────────────────────────────────────────────

    @Test
    void getOrdersByUserId_found_returnsUserOrdersPage() {
        User user = makeUser(1L);
        PageImpl<Order> page = new PageImpl<>(List.of(
                makeOrder(1L, user, OrderStatus.CREATED, new BigDecimal("1000.00"))
        ));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(orderRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(page);

        Page<OrderResponse> result = orderService.getOrdersByUserId(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void getOrdersByUserId_noOrders_returnsEmptyPage() {
        User user = makeUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(orderRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(orderService.getOrdersByUserId(1L, Pageable.unpaged()).getContent()).isEmpty();
    }

    @Test
    void getOrdersByUserId_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrdersByUserId(99L, Pageable.unpaged()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── getOrderById ──────────────────────────────────────────────────────────

    @Test
    void getOrderById_found_returnsResponse() {
        User user = makeUser(1L);
        Order order = makeOrder(1L, user, OrderStatus.PAID, new BigDecimal("7500.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("7500.00");
    }

    @Test
    void getOrderById_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_found_changesStatusAndSaves() {
        User user = makeUser(1L);
        Order order = makeOrder(1L, user, OrderStatus.PAID, new BigDecimal("5000.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse result = orderService.updateStatus(1L, OrderStatus.DELIVERED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderRepository).save(order);
    }

    @Test
    void updateStatus_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(99L, OrderStatus.DELIVERED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_allStatusValues_areAccepted() {
        User user = makeUser(1L);
        for (OrderStatus status : OrderStatus.values()) {
            Order order = makeOrder(1L, user, OrderStatus.CREATED, BigDecimal.TEN);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            OrderResponse result = orderService.updateStatus(1L, status);

            assertThat(result.getStatus()).isEqualTo(status);
        }
    }
}
