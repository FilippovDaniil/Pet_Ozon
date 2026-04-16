package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    public List<ProductResponse> getSellerProducts(Long sellerId) {
        User seller = resolveSeller(sellerId);
        return productRepository.findBySeller(seller).stream()
                .map(productService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse createProduct(Long sellerId, CreateProductRequest request) {
        User seller = resolveSeller(sellerId);
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setSeller(seller);
        return productService.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long productId, CreateProductRequest request) {
        Product product = resolveSellerProduct(sellerId, productId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        return productService.toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long sellerId, Long productId) {
        Product product = resolveSellerProduct(sellerId, productId);
        productRepository.delete(product);
    }

    public SellerResponse getBalance(Long sellerId) {
        User seller = resolveSeller(sellerId);
        SellerResponse r = new SellerResponse();
        r.setId(seller.getId());
        r.setEmail(seller.getEmail());
        r.setFullName(seller.getFullName());
        r.setShopName(seller.getShopName());
        r.setBalance(seller.getBalance());
        return r;
    }

    public List<Order> getSellerOrders(Long sellerId) {
        resolveSeller(sellerId);
        return orderRepository.findBySellerId(sellerId);
    }

    private User resolveSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + sellerId));
        if (user.getRole() != Role.SELLER) {
            throw new IllegalArgumentException("User " + sellerId + " is not a seller");
        }
        return user;
    }

    private Product resolveSellerProduct(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (product.getSeller() == null || !product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("Product does not belong to this seller");
        }
        return product;
    }
}
