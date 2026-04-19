package com.example.marketplace.service;

import com.example.marketplace.dto.response.CartItemResponse;
import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.dto.response.OrderItemResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.InvoiceStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        Cart cart = findCartByUserId(userId);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addToCart(Long userId, Long productId, int quantity) {
        Cart cart = findCartByUserId(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Optional<CartItem> existing = cartItemRepository.findByCartAndProduct(cart, product);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            CartItem saved = cartItemRepository.save(item);
            cart.getItems().add(saved);
        }

        return toCartResponse(cart);
    }

    @Transactional
    public void removeFromCart(Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem not found with id: " + cartItemId));
        cartItemRepository.delete(item);
    }

    @Transactional
    public CartResponse updateQuantity(Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem not found with id: " + cartItemId));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        // Load cart fresh to reflect changes
        Cart cart = cartRepository.findById(item.getCart().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        return toCartResponse(cart);
    }

    @Transactional
    public OrderResponse checkout(Long userId, String shippingAddress) {
        Cart cart = findCartByUserId(userId);
        List<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Pre-check stock availability
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new IllegalArgumentException(
                        "Недостаточно товара «" + product.getName() + "» на складе. " +
                        "Доступно: " + product.getStockQuantity());
            }
        }

        BigDecimal total = cartItems.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create Order
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(total);
        order.setShippingAddress(shippingAddress);
        orderRepository.save(order);

        // Create OrderItems (snapshot of cart) and reduce stock
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtOrder(product.getPrice());
            orderItemRepository.save(orderItem);
            order.getItems().add(orderItem);

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        // Create Invoice
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setAmount(total);
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoiceRepository.save(invoice);

        // Clear cart
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Оформлен заказ id={} пользователь id={} сумма={}", order.getId(), userId, total);
        return toOrderResponse(order);
    }

    private Cart findCartByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user id: " + userId));
    }

    private CartResponse toCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());
        response.setItems(itemResponses);
        BigDecimal total = itemResponses.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        response.setTotalPrice(total);
        return response;
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        CartItemResponse r = new CartItemResponse();
        r.setId(item.getId());
        r.setProductId(item.getProduct().getId());
        r.setProductName(item.getProduct().getName());
        r.setQuantity(item.getQuantity());
        r.setPrice(item.getProduct().getPrice());
        return r;
    }

    private OrderResponse toOrderResponse(Order order) {
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
        return r;
    }
}
