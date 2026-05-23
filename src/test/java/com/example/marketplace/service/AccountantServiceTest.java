package com.example.marketplace.service;

import com.example.marketplace.dto.response.*;
import com.example.marketplace.entity.*;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Юнит-тесты для AccountantService — проверяем формирование отчётов.
// @PreAuthorize на уровне класса игнорируется: Spring Security не загружается в MockitoExtension.
// Это правильное поведение для юнит-тестов — авторизация проверяется отдельно в тестах контроллера.
@ExtendWith(MockitoExtension.class)
class AccountantServiceTest {

    // Моки всех репозиториев, которые использует AccountantService
    @Mock OrderRepository    orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock UserRepository     userRepository;
    @Mock EmailLogRepository emailLogRepository;

    // AccountantService создаётся с подставленными выше моками
    @InjectMocks
    AccountantService accountantService;

    // ── Вспомогательные методы ───────────────────────────────────────────────

    // Создаёт пользователя-клиента с заданным id и именем
    private User makeClient(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setEmail(name.toLowerCase().replace(" ", "") + "@example.com");
        u.setFullName(name);
        u.setRole(Role.CLIENT);
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    // Создаёт оплаченный заказ: статус PAID, сумма totalAmount
    private Order makePaidOrder(User user, BigDecimal amount) {
        Order o = new Order();
        o.setId(1L);
        o.setUser(user);
        o.setStatus(OrderStatus.PAID);
        o.setTotalAmount(amount);
        o.setOrderDate(LocalDateTime.now());
        o.setItems(new ArrayList<>());
        return o;
    }

    // Создаёт неоплаченный заказ: статус CREATED, баланс в выручку не входит
    private Order makeCreatedOrder(User user, BigDecimal amount) {
        Order o = new Order();
        o.setId(2L);
        o.setUser(user);
        o.setStatus(OrderStatus.CREATED);
        o.setTotalAmount(amount);
        o.setOrderDate(LocalDateTime.now());
        o.setItems(new ArrayList<>());
        return o;
    }

    // Создаёт позицию корзины: товар с заданной ценой и количеством
    private CartItem makeCartItem(User user, String productName, BigDecimal price, int qty) {
        Product product = new Product();
        product.setName(productName);
        product.setCategory(new com.example.marketplace.entity.Category("Электроника"));
        product.setPrice(price);

        Cart cart = new Cart();
        cart.setUser(user);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    // Создаёт запись лога отправки письма
    private EmailLog makeEmailLog(Long id, String recipient, boolean success) {
        EmailLog log = new EmailLog();
        log.setId(id);
        log.setRecipient(recipient);
        log.setSubject("Тестовое письмо");
        log.setSentAt(LocalDateTime.now());
        log.setSuccess(success);
        return log;
    }

    // ── getSummary ────────────────────────────────────────────────────────────

    @Test
    void getSummary_withPaidAndUnpaidOrders_calculatesRevenueFromPaidOnly() {
        // Два заказа: один оплачен (5000), другой нет (3000) — в выручку идёт только оплаченный
        User client = makeClient(1L, "Иван");
        Order paid    = makePaidOrder(client, new BigDecimal("5000.00"));
        Order unpaid  = makeCreatedOrder(client, new BigDecimal("3000.00"));

        when(orderRepository.findAll()).thenReturn(List.of(paid, unpaid));
        when(cartItemRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByRole(Role.CLIENT)).thenReturn(List.of(client));
        when(emailLogRepository.count()).thenReturn(2L);
        when(emailLogRepository.countBySuccess(true)).thenReturn(1L);

        AccountantSummaryResponse summary = accountantService.getSummary();

        assertThat(summary.getTotalOrders()).isEqualTo(2);        // итого заказов
        assertThat(summary.getPaidOrders()).isEqualTo(1);         // только оплаченных
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("5000.00"); // только PAID
        assertThat(summary.getTotalClients()).isEqualTo(1);
        assertThat(summary.getEmailsSent()).isEqualTo(2);
        assertThat(summary.getEmailsSuccess()).isEqualTo(1);
    }

    @Test
    void getSummary_emptyDatabase_returnsAllZeros() {
        // Граничный случай: в БД ещё нет ни одной записи
        when(orderRepository.findAll()).thenReturn(List.of());
        when(cartItemRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByRole(Role.CLIENT)).thenReturn(List.of());
        when(emailLogRepository.count()).thenReturn(0L);
        when(emailLogRepository.countBySuccess(true)).thenReturn(0L);

        AccountantSummaryResponse summary = accountantService.getSummary();

        assertThat(summary.getTotalOrders()).isZero();
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalClients()).isZero();
        assertThat(summary.getCartItemsCount()).isZero();
        assertThat(summary.getPotentialRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getSummary_cartItems_calculatesPotentialRevenue() {
        // Потенциальная выручка = сумма (цена × количество) по всем позициям корзин
        User client = makeClient(1L, "Иван");
        // 2 ноутбука по 50 000 + 1 мышь за 5 000 = 105 000
        CartItem laptop = makeCartItem(client, "Ноутбук", new BigDecimal("50000.00"), 2);
        CartItem mouse  = makeCartItem(client, "Мышь",    new BigDecimal("5000.00"),  1);

        when(orderRepository.findAll()).thenReturn(List.of());
        when(cartItemRepository.findAll()).thenReturn(List.of(laptop, mouse));
        when(userRepository.findByRole(Role.CLIENT)).thenReturn(List.of());
        when(emailLogRepository.count()).thenReturn(0L);
        when(emailLogRepository.countBySuccess(true)).thenReturn(0L);

        AccountantSummaryResponse summary = accountantService.getSummary();

        assertThat(summary.getCartItemsCount()).isEqualTo(2);
        assertThat(summary.getPotentialRevenue()).isEqualByComparingTo("105000.00");
    }

    // ── getOrdersReport ───────────────────────────────────────────────────────

    @Test
    void getOrdersReport_returnsMappedDtos() {
        // Проверяем корректное отображение Order → OrderReportDto
        User client = makeClient(1L, "Пётр Иванов");
        Order order = makePaidOrder(client, new BigDecimal("12000.00"));
        order.setId(7L);
        order.setShippingAddress("Москва, ул. Примерная, 1");

        // findAll(Sort) — перегрузка JpaRepository с сортировкой по полю
        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(order));

        List<OrderReportDto> result = accountantService.getOrdersReport();

        assertThat(result).hasSize(1);
        OrderReportDto dto = result.get(0);
        assertThat(dto.getOrderId()).isEqualTo(7L);
        assertThat(dto.getCustomerName()).isEqualTo("Пётр Иванов");
        assertThat(dto.getCustomerEmail()).isEqualTo("пётриванов@example.com");
        assertThat(dto.getStatus()).isEqualTo("PAID");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("12000.00");
        assertThat(dto.getShippingAddress()).isEqualTo("Москва, ул. Примерная, 1");
    }

    @Test
    void getOrdersReport_userHasNoFullName_usesEmailAsFallback() {
        // Если fullName не задан — в отчёт идёт email
        User client = new User();
        client.setId(1L);
        client.setEmail("noname@example.com");
        client.setFullName(null); // имя не задано
        client.setRole(Role.CLIENT);

        Order order = makePaidOrder(client, new BigDecimal("1000.00"));
        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(order));

        List<OrderReportDto> result = accountantService.getOrdersReport();

        // Сервис должен использовать email как имя при отсутствии fullName
        assertThat(result.get(0).getCustomerName()).isEqualTo("noname@example.com");
    }

    @Test
    void getOrdersReport_emptyList_returnsEmpty() {
        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(accountantService.getOrdersReport()).isEmpty();
    }

    // ── getCartsReport ────────────────────────────────────────────────────────

    @Test
    void getCartsReport_calculatesSubtotals() {
        // Проверяем что subtotal = price × quantity
        User client = makeClient(1L, "Анна Петрова");
        // 3 наушника по 15 000 = subtotal 45 000
        CartItem item = makeCartItem(client, "Наушники Sony", new BigDecimal("15000.00"), 3);

        when(cartItemRepository.findAll()).thenReturn(List.of(item));

        List<CartReportDto> result = accountantService.getCartsReport();

        assertThat(result).hasSize(1);
        CartReportDto dto = result.get(0);
        assertThat(dto.getCustomerName()).isEqualTo("Анна Петрова");
        assertThat(dto.getProductName()).isEqualTo("Наушники Sony");
        assertThat(dto.getCategory()).isEqualTo("Электроника");
        assertThat(dto.getQuantity()).isEqualTo(3);
        assertThat(dto.getPrice()).isEqualByComparingTo("15000.00");
        assertThat(dto.getSubtotal()).isEqualByComparingTo("45000.00"); // 15000 × 3
    }

    @Test
    void getCartsReport_emptyCart_returnsEmpty() {
        when(cartItemRepository.findAll()).thenReturn(List.of());

        assertThat(accountantService.getCartsReport()).isEmpty();
    }

    // ── getCustomersReport ────────────────────────────────────────────────────

    @Test
    void getCustomersReport_sumsOnlyPaidOrders() {
        // totalSpent должна включать только оплаченные заказы, не все подряд
        User client = makeClient(1L, "Сергей");
        Order paid   = makePaidOrder(client, new BigDecimal("8000.00"));
        Order unpaid = makeCreatedOrder(client, new BigDecimal("2000.00")); // не учитывается

        when(userRepository.findByRole(Role.CLIENT)).thenReturn(List.of(client));
        // findByUser возвращает оба заказа — но только PAID попадёт в totalSpent
        when(orderRepository.findByUser(client)).thenReturn(List.of(paid, unpaid));

        List<CustomerReportDto> result = accountantService.getCustomersReport();

        assertThat(result).hasSize(1);
        CustomerReportDto dto = result.get(0);
        assertThat(dto.getFullName()).isEqualTo("Сергей");
        assertThat(dto.getOrdersCount()).isEqualTo(2);       // всего заказов (включая неоплаченные)
        assertThat(dto.getTotalSpent()).isEqualByComparingTo("8000.00"); // только PAID
    }

    @Test
    void getCustomersReport_noClients_returnsEmpty() {
        when(userRepository.findByRole(Role.CLIENT)).thenReturn(List.of());

        assertThat(accountantService.getCustomersReport()).isEmpty();
    }

    // ── getEmailsReport ───────────────────────────────────────────────────────

    @Test
    void getEmailsReport_returnsDtosWithCorrectFields() {
        EmailLog success = makeEmailLog(1L, "buyer@example.com", true);
        EmailLog failure = makeEmailLog(2L, "other@example.com", false);
        failure.setErrorMessage("Connection timeout");

        // findAll(Sort) возвращает записи в порядке убывания даты
        when(emailLogRepository.findAll(any(Sort.class))).thenReturn(List.of(success, failure));

        List<EmailLogDto> result = accountantService.getEmailsReport();

        assertThat(result).hasSize(2);

        // Первая запись — успешная
        assertThat(result.get(0).getRecipient()).isEqualTo("buyer@example.com");
        assertThat(result.get(0).isSuccess()).isTrue();
        assertThat(result.get(0).getErrorMessage()).isNull();

        // Вторая запись — с ошибкой
        assertThat(result.get(1).getRecipient()).isEqualTo("other@example.com");
        assertThat(result.get(1).isSuccess()).isFalse();
        assertThat(result.get(1).getErrorMessage()).isEqualTo("Connection timeout");
    }

    @Test
    void getEmailsReport_noEmails_returnsEmpty() {
        when(emailLogRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(accountantService.getEmailsReport()).isEmpty();
    }
}
