package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getProductById(Long id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, CreateProductRequest request) {
        Product product = findEntityById(id);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    public Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public ProductResponse toResponse(Product product) {
        ProductResponse r = new ProductResponse();
        r.setId(product.getId());
        r.setName(product.getName());
        r.setDescription(product.getDescription());
        r.setPrice(product.getPrice());
        r.setStockQuantity(product.getStockQuantity());
        r.setImageUrl(product.getImageUrl());
        if (product.getSeller() != null) {
            r.setSellerId(product.getSeller().getId());
            r.setSellerName(product.getSeller().getFullName());
            r.setShopName(product.getSeller().getShopName());
        }
        return r;
    }
}
