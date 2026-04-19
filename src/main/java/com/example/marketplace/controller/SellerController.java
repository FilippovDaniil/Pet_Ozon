package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;
    private final OrderService orderService;

    @GetMapping("/products")
    public List<ProductResponse> getMyProducts(@AuthenticationPrincipal User user) {
        return sellerService.getSellerProducts(user.getId());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@AuthenticationPrincipal User user,
                                                          @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(201).body(sellerService.createProduct(user.getId(), request));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@AuthenticationPrincipal User user,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CreateProductRequest request) {
        return sellerService.updateProduct(user.getId(), id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal User user,
                                               @PathVariable Long id) {
        sellerService.deleteProduct(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/balance")
    public SellerResponse getBalance(@AuthenticationPrincipal User user) {
        return sellerService.getBalance(user.getId());
    }

    @GetMapping("/sales")
    public List<OrderResponse> getSales(@AuthenticationPrincipal User user) {
        return sellerService.getSellerOrders(user.getId()).stream()
                .map(orderService::toResponse)
                .collect(Collectors.toList());
    }
}
