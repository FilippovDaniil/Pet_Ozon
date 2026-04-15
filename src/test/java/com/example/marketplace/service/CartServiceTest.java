package com.example.marketplace.service;

import com.example.marketplace.dto.response.CartResponse;
import com.example.marketplace.dto.response.OrderResponse;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository       cartRepository;
    @Mock CartItemRepository   cartItemRepository;
    @Mock ProductRepository    productRepository;
    @Mock UserRepository       userRepository;
    @Mock OrderRepository      orderRepository;
    @Mock OrderItemRepository  orderItemRepository;
    @Mock InvoiceRepository    invoiceRepository;

    @InjectMocks
    CartService cartService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private Product makeProduct(Long id, String name, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStockQuantity(100);
        return p;
    }

    private Cart makeCart(Long id, User user) {
        Cart c = new Cart();
        c.setId(id);
        c.setUser(user);
        c.setItems(new ArrayList<>());
        return c;
    }

    private CartItem makeCartItem(Long id, Cart cart, Product product, int qty) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    private void stubFindCart(User user, Cart cart) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
    }

    // ── getCartByUserId ───────────────────────────────────────────────────────

    @Test
    void getCartByUserId_emptyCart_returnszeroTotal() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        stubFindCart(user, cart);

        CartResponse result = cartService.getCartByUserId(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCartByUserId_withItems_calculatesTotalCorrectly() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product p = makeProduct(1L, "Ноутбук", new BigDecimal("50000.00"));
        cart.getItems().add(makeCartItem(1L, cart, p, 2)); // 2 × 50000 = 100000
        stubFindCart(user, cart);

        CartResponse result = cartService.getCartByUserId(1L);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("100000.00");
    }

    @Test
    void getCartByUserId_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCartByUserId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getCartByUserId_cartNotFound_throwsException() {
        User user = makeUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCartByUserId(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart not found");
    }

    // ── addToCart ─────────────────────────────────────────────────────────────

    @Test
    void addToCart_newProduct_createsCartItem() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Мышь", new BigDecimal("1999.00"));
        stubFindCart(user, cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartAndProduct(cart, product)).thenReturn(Optional.empty());

        CartItem saved = makeCartItem(1L, cart, product, 3);
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(saved);

        CartResponse result = cartService.addToCart(1L, 1L, 3);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addToCart_existingProduct_increasesQuantity() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Мышь", new BigDecimal("1999.00"));
        CartItem existing = makeCartItem(1L, cart, product, 2);
        cart.getItems().add(existing);
        stubFindCart(user, cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartAndProduct(cart, product)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);

        cartService.addToCart(1L, 1L, 3);

        assertThat(existing.getQuantity()).isEqualTo(5); // 2 + 3
        verify(cartItemRepository).save(existing);
    }

    @Test
    void addToCart_productNotFound_throwsException() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        stubFindCart(user, cart);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(1L, 99L, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    // ── removeFromCart ────────────────────────────────────────────────────────

    @Test
    void removeFromCart_found_deletesItem() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        CartItem item = makeCartItem(1L, cart, makeProduct(1L, "Мышь", BigDecimal.TEN), 2);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        cartService.removeFromCart(1L);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeFromCart_notFound_throwsException() {
        when(cartItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeFromCart(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CartItem not found");
    }

    // ── updateQuantity ────────────────────────────────────────────────────────

    @Test
    void updateQuantity_valid_setsNewQuantity() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Мышь", new BigDecimal("1999.00"));
        CartItem item = makeCartItem(1L, cart, product, 2);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(item)).thenReturn(item);
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        cartService.updateQuantity(1L, 10);

        assertThat(item.getQuantity()).isEqualTo(10);
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateQuantity_zero_throwsIllegalArgument() {
        assertThatThrownBy(() -> cartService.updateQuantity(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than 0");
    }

    @Test
    void updateQuantity_negative_throwsIllegalArgument() {
        assertThatThrownBy(() -> cartService.updateQuantity(1L, -3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── checkout ──────────────────────────────────────────────────────────────

    @Test
    void checkout_success_createsOrderAndInvoiceClearsCart() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Ноутбук", new BigDecimal("50000.00"));
        cart.getItems().add(makeCartItem(1L, cart, product, 2)); // total = 100000
        stubFindCart(user, cart);

        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = cartService.checkout(1L, "Москва, ул. Тестовая, 1");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.getShippingAddress()).isEqualTo("Москва, ул. Тестовая, 1");
        assertThat(result.getItems()).hasSize(1);

        verify(orderRepository).save(any(Order.class));
        verify(invoiceRepository).save(any(Invoice.class));
        verify(cartRepository).save(cart);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void checkout_multipleItems_calculatesCorrectTotal() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product p1 = makeProduct(1L, "Ноутбук", new BigDecimal("80000.00"));
        Product p2 = makeProduct(2L, "Мышь", new BigDecimal("2000.00"));
        cart.getItems().add(makeCartItem(1L, cart, p1, 1)); // 80000
        cart.getItems().add(makeCartItem(2L, cart, p2, 3)); // 6000
        stubFindCart(user, cart);
        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = cartService.checkout(1L, "Адрес");

        assertThat(result.getTotalAmount()).isEqualByComparingTo("86000.00");
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    void checkout_emptyCart_throwsIllegalArgument() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user); // empty items
        stubFindCart(user, cart);

        assertThatThrownBy(() -> cartService.checkout(1L, "Адрес"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void checkout_snapshotsPriceAtOrderTime() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Товар", new BigDecimal("999.00"));
        cart.getItems().add(makeCartItem(1L, cart, product, 1));
        stubFindCart(user, cart);
        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(inv -> {
                    OrderItem oi = inv.getArgument(0);
                    assertThat(oi.getPriceAtOrder()).isEqualByComparingTo("999.00");
                    return oi;
                });

        cartService.checkout(1L, "Адрес");

        verify(orderItemRepository).save(any(OrderItem.class));
    }
}
