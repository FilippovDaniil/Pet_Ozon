package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.request.UpdateOrderStatusRequest;
import com.example.marketplace.dto.response.InvoiceResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.service.InvoiceService;
import com.example.marketplace.service.OrderService;
import com.example.marketplace.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;

    // --- Products ---

    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @RequestBody CreateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    // --- Orders ---

    @GetMapping("/orders")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @PutMapping(value = "/orders/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse updateOrderStatus(
            @PathVariable Long id,
            @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.getStatus());
    }

    // --- Invoices ---

    @GetMapping("/invoices")
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }
}
