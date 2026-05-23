package com.example.marketplace.controller;

import com.example.marketplace.search.ProductDocument;
import com.example.marketplace.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

// Публичный эндпоинт полнотекстового поиска через OpenSearch.
// GET /api/search/products?q=ноутбук&category=Ноутбуки&minPrice=50000&maxPrice=200000&page=0&size=10
// Доступен без аутентификации — поиск открыт для всех.
@RestController
@RequestMapping(value = "/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/products")
    public Page<ProductDocument> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {
        return productSearchService.search(q, category, minPrice, maxPrice, pageable);
    }
}
