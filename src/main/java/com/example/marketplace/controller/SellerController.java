package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;
    private final OrderService orderService;

    private Long sellerId(String header) {
        if (header != null && !header.isBlank()) return Long.parseLong(header);
        return 3L;
    }

    @GetMapping("/products")
    public List<ProductResponse> getMyProducts(
            @RequestHeader(value = "X-User-Id", required = false) String h) {
        return sellerService.getSellerProducts(sellerId(h));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestHeader(value = "X-User-Id", required = false) String h,
            @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(201).body(sellerService.createProduct(sellerId(h), request));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(
            @RequestHeader(value = "X-User-Id", required = false) String h,
            @PathVariable Long id,
            @RequestBody CreateProductRequest request) {
        return sellerService.updateProduct(sellerId(h), id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader(value = "X-User-Id", required = false) String h,
            @PathVariable Long id) {
        sellerService.deleteProduct(sellerId(h), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/balance")
    public SellerResponse getBalance(
            @RequestHeader(value = "X-User-Id", required = false) String h) {
        return sellerService.getBalance(sellerId(h));
    }

    @GetMapping("/sales")
    public List<OrderResponse> getSales(
            @RequestHeader(value = "X-User-Id", required = false) String h) {
        return sellerService.getSellerOrders(sellerId(h)).stream()
                .map(orderService::toResponse)
                .collect(Collectors.toList());
    }
}
