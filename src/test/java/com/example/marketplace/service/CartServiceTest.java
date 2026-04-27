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

// @ExtendWith(MockitoExtension.class) — подключает Mockito к JUnit 5.
// Mockito автоматически инициализирует поля @Mock и @InjectMocks перед каждым тестом.
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    // @Mock создаёт «заглушку» (фиктивный объект) вместо реального репозитория.
    // Мок не обращается к БД — он просто возвращает то, что ему скажут через when(...).thenReturn(...)
    @Mock CartRepository       cartRepository;
    @Mock CartItemRepository   cartItemRepository;
    @Mock ProductRepository    productRepository;
    @Mock UserRepository       userRepository;
    @Mock OrderRepository      orderRepository;
    @Mock OrderItemRepository  orderItemRepository;
    @Mock InvoiceRepository    invoiceRepository;

    // @InjectMocks создаёт реальный объект CartService и передаёт в него все @Mock-поля выше.
    // Это позволяет тестировать логику сервиса без реальной базы данных.
    @InjectMocks
    CartService cartService;

    // ── helpers ───────────────────────────────────────────────────────────────
    // Вспомогательные методы для создания тестовых объектов.
    // Выделены в отдельные методы, чтобы не дублировать код в каждом тесте.

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
        c.setItems(new ArrayList<>()); // пустой список позиций
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

    // stubFindCart — настраивает моки так, чтобы при поиске пользователя и его корзины
    // возвращались заданные объекты. Используется в большинстве тестов.
    private void stubFindCart(User user, Cart cart) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
    }

    // ── getCartByUserId ───────────────────────────────────────────────────────

    @Test
    void getCartByUserId_emptyCart_returnszeroTotal() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        stubFindCart(user, cart); // настроить моки: найти юзера и его пустую корзину

        CartResponse result = cartService.getCartByUserId(1L);

        // assertThat — метод AssertJ для читаемых проверок вместо assertEquals(expected, actual)
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getItems()).isEmpty();
        // isEqualByComparingTo для BigDecimal: сравнивает значение, игнорируя масштаб (0 == 0.00)
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
        // Настраиваем мок: при поиске id=99 возвращать Optional.empty() (пользователь не найден)
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // assertThatThrownBy проверяет, что вызов метода бросает ожидаемое исключение
        assertThatThrownBy(() -> cartService.getCartByUserId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getCartByUserId_cartNotFound_throwsException() {
        User user = makeUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.empty()); // корзина не найдена

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
        // Optional.empty() означает: этого товара ещё нет в корзине → нужно создать новую позицию
        when(cartItemRepository.findByCartAndProduct(cart, product)).thenReturn(Optional.empty());

        CartItem saved = makeCartItem(1L, cart, product, 3);
        // any(CartItem.class) — принять любой объект CartItem при вызове save
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(saved);

        CartResponse result = cartService.addToCart(1L, 1L, 3);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
        // verify проверяет, что метод save действительно был вызван
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addToCart_existingProduct_increasesQuantity() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Мышь", new BigDecimal("1999.00"));
        CartItem existing = makeCartItem(1L, cart, product, 2); // уже 2 штуки в корзине
        cart.getItems().add(existing);
        stubFindCart(user, cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        // Возвращаем существующую позицию → сервис должен увеличить количество, а не создавать новое
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

        // Проверяем что delete был вызван именно с этим объектом
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

        assertThat(item.getQuantity()).isEqualTo(10); // количество обновлено
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateQuantity_zero_throwsIllegalArgument() {
        // Нулевое количество — нарушение бизнес-правила.
        // Сервис должен бросить IllegalArgumentException ещё до обращения к БД.
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

        // thenAnswer — вернуть сам переданный аргумент (вместо создания нового объекта).
        // inv.getArgument(0) — первый аргумент вызова метода save (объект OrderItem).
        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = cartService.checkout(1L, "Москва, ул. Тестовая, 1");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.getShippingAddress()).isEqualTo("Москва, ул. Тестовая, 1");
        assertThat(result.getItems()).hasSize(1);

        // Проверяем все ключевые шаги checkout: заказ, счёт, очистка корзины
        verify(orderRepository).save(any(Order.class));
        verify(invoiceRepository).save(any(Invoice.class));
        verify(cartRepository).save(cart);
        assertThat(cart.getItems()).isEmpty(); // корзина должна быть очищена
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

        // 80000 + 6000 = 86000
        assertThat(result.getTotalAmount()).isEqualByComparingTo("86000.00");
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    void checkout_emptyCart_throwsIllegalArgument() {
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user); // пустая корзина — нет товаров
        stubFindCart(user, cart);

        assertThatThrownBy(() -> cartService.checkout(1L, "Адрес"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void checkout_snapshotsPriceAtOrderTime() {
        // Тест проверяет «снимок» цены: в OrderItem должна сохраниться цена товара на момент заказа,
        // а не текущая цена (которая может измениться позже).
        User user = makeUser(1L);
        Cart cart = makeCart(1L, user);
        Product product = makeProduct(1L, "Товар", new BigDecimal("999.00"));
        cart.getItems().add(makeCartItem(1L, cart, product, 1));
        stubFindCart(user, cart);
        when(orderItemRepository.save(any(OrderItem.class)))
                .thenAnswer(inv -> {
                    OrderItem oi = inv.getArgument(0);
                    // Проверяем внутри лямбды: цена снимка должна совпадать с ценой товара
                    assertThat(oi.getPriceAtOrder()).isEqualByComparingTo("999.00");
                    return oi;
                });

        cartService.checkout(1L, "Адрес");

        verify(orderItemRepository).save(any(OrderItem.class));
    }
}
