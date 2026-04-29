package com.example.marketplace.service;

import com.example.marketplace.dto.response.OrderItemResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.InvoiceRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с заказами.
 *
 * Отвечает за просмотр заказов и смену статуса (только для Admin).
 * Создание заказа — в CartService.checkout().
 *
 * toResponse() объявлен public: его переиспользует SellerController
 * для конвертации заказов продавца.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    /** Все заказы — только для Admin. Возвращает постранично. */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    /** Заказы конкретного пользователя — с пагинацией. */
    public Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return orderRepository.findByUser(user, pageable).map(this::toResponse);
    }

    public OrderResponse getOrderById(Long id) {
        return toResponse(findEntityById(id));
    }

    /**
     * Смена статуса заказа — используется Admin-ом.
     * Например: PAID → DELIVERED или CREATED → CANCELLED.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = findEntityById(id);
        order.setStatus(status);
        log.info("Статус заказа id={} изменён на {}", id, status);
        return toResponse(orderRepository.save(order));
    }

    public Order findEntityById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    /**
     * Конвертирует Order в DTO.
     * Дополнительно подтягивает invoiceId, если счёт уже создан.
     * ifPresent — безопасно: не бросает исключение, если Invoice не найден.
     */
    public OrderResponse toResponse(Order order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setOrderDate(order.getOrderDate());
        r.setStatus(order.getStatus());
        r.setTotalAmount(order.getTotalAmount());
        r.setShippingAddress(order.getShippingAddress());
        r.setItems(order.getItems().stream().map(item -> {
            OrderItemResponse ir = new OrderItemResponse();
            ir.setId(item.getId());
            ir.setProductId(item.getProduct().getId());
            ir.setProductName(item.getProduct().getName());
            ir.setQuantity(item.getQuantity());
            ir.setPriceAtOrder(item.getPriceAtOrder());
            return ir;
        }).collect(Collectors.toList()));
        // Добавляем ID счёта в ответ, если он существует.
        invoiceRepository.findByOrder(order)
                .ifPresent(inv -> r.setInvoiceId(inv.getId()));
        return r;
    }
}
