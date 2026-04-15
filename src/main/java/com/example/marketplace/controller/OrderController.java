package com.example.marketplace.controller;

import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public List<OrderResponse> getMyOrders(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = (xUserId != null && !xUserId.isBlank()) ? Long.parseLong(xUserId) : 1L;
        return orderService.getOrdersByUserId(userId);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }
}
