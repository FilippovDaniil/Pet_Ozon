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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис корзины и оформления заказа — «сердце» бизнес-логики маркетплейса.
 *
 * Главный метод — checkout(): превращает корзину в заказ.
 * Алгоритм checkout:
 *   1. Проверяем наличие товаров на складе.
 *   2. Считаем итоговую сумму.
 *   3. Создаём Order с позициями OrderItem (снимок цен).
 *   4. Уменьшаем stockQuantity у каждого товара.
 *   5. Создаём Invoice со статусом UNPAID.
 *   6. Очищаем корзину.
 * Всё это — в одной транзакции (@Transactional).
 */
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

    // isAuthenticated() — любой вошедший пользователь может работать с корзиной.
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        Cart cart = findCartByUserId(userId);
        return toCartResponse(cart);
    }

    /**
     * Добавляет товар в корзину.
     * Если товар уже есть — увеличивает quantity (не создаёт дублей).
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public CartResponse addToCart(Long userId, Long productId, int quantity) {
        Cart cart = findCartByUserId(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // Проверяем: есть ли уже такой товар в корзине?
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

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void removeFromCart(Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem not found with id: " + cartItemId));
        cartItemRepository.delete(item);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public CartResponse updateQuantity(Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem not found with id: " + cartItemId));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        Cart cart = cartRepository.findById(item.getCart().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        return toCartResponse(cart);
    }

    /**
     * Оформление заказа: атомарно превращает корзину в Order + Invoice.
     *
     * @Transactional гарантирует: либо ВСЕ изменения применяются, либо НИ ОДНОГО.
     * Например, если при создании 5-го OrderItem произойдёт ошибка —
     * вся операция откатится, и stock не уменьшится, и Order не создастся.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public OrderResponse checkout(Long userId, String shippingAddress) {
        Cart cart = findCartByUserId(userId);
        List<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Предварительная проверка: достаточно ли товара на складе?
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new IllegalArgumentException(
                        "Недостаточно товара «" + product.getName() + "» на складе. " +
                        "Доступно: " + product.getStockQuantity());
            }
        }

        // reduce() суммирует все позиции: BigDecimal.ZERO — начальное значение.
        BigDecimal total = cartItems.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Создаём заказ.
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(total);
        order.setShippingAddress(shippingAddress);
        orderRepository.save(order);

        // Создаём позиции заказа (снимок корзины) и списываем остаток.
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            // Фиксируем цену на момент заказа — она не изменится при будущих изменениях товара.
            orderItem.setPriceAtOrder(product.getPrice());
            orderItemRepository.save(orderItem);
            order.getItems().add(orderItem);

            // Уменьшаем остаток на складе.
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        // Создаём счёт на оплату.
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setAmount(total);
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoiceRepository.save(invoice);

        // Очищаем корзину — позиции удаляются каскадно благодаря orphanRemoval = true.
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
