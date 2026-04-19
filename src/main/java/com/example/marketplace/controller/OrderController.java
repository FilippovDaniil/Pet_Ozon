package com.example.marketplace.controller;

import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public Page<OrderResponse> getMyOrders(@AuthenticationPrincipal User user,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.getOrdersByUserId(user.getId(), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }
}
