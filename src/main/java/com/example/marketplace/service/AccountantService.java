package com.example.marketplace.service;

import com.example.marketplace.dto.response.*;
import com.example.marketplace.entity.BnplContract;
import com.example.marketplace.entity.BnplPayment;
import com.example.marketplace.entity.CartItem;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.BnplContractStatus;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// Сервис бизнес-логики для бухгалтерских отчётов.
// @PreAuthorize на уровне класса: каждый публичный метод доступен только с ролью ACCOUNTANT.
// Это второй уровень защиты — первый настроен в SecurityConfig по URL /api/accountant/**.
// Двойная защита гарантирует, что даже прямой вызов бина из кода не обойдёт авторизацию.
@Service
// Lombok: генерирует конструктор со всеми final-полями; Spring инжектирует зависимости через него
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantService {

    // Четыре репозитория — каждый отвечает за одну таблицу БД.
    // Инжектируются через конструктор (@RequiredArgsConstructor + final).
    private final OrderRepository       orderRepository;
    private final CartItemRepository    cartItemRepository;
    private final UserRepository        userRepository;
    private final EmailLogRepository    emailLogRepository;
    private final BnplContractRepository bnplContractRepository;
    private final BnplPaymentRepository  bnplPaymentRepository;

    // readOnly=true: Hibernate не отслеживает изменения (dirty checking отключён),
    // что ускоряет запросы только на чтение — сессия не нужна для flush().
    @Transactional(readOnly = true)
    public AccountantSummaryResponse getSummary() {
        // Загружаем все заказы одним SELECT * FROM orders; дальше фильтруем в памяти
        List<Order> allOrders = orderRepository.findAll();

        // Java Streams: filter() выбирает PAID-заказы, count() считает их количество
        long paidOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();

        // reduce(начальное_значение, аккумулятор) — аналог SQL SUM(), но в памяти.
        // BigDecimal.ZERO — стартовое значение; BigDecimal::add — функция сложения.
        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .map(Order::getTotalAmount)          // берём поле totalAmount из каждого заказа
                .reduce(BigDecimal.ZERO, BigDecimal::add); // суммируем

        // findByRole — кастомный Spring Data метод: SELECT * FROM users WHERE role = 'CLIENT'
        long totalClients = userRepository.findByRole(Role.CLIENT).size();

        // Загружаем все позиции корзин для подсчёта количества и потенциальной выручки
        List<CartItem> cartItems = cartItemRepository.findAll();

        // Потенциальная выручка: для каждой позиции корзины перемножаем цену и количество
        BigDecimal potentialRevenue = cartItems.stream()
                .map(ci -> ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // count() — стандартный JpaRepository-метод: SELECT COUNT(*) FROM email_logs
        long emailsSent    = emailLogRepository.count();
        // countBySuccess(true) — Spring Data генерирует: SELECT COUNT(*) WHERE success = true
        long emailsSuccess = emailLogRepository.countBySuccess(true);

        // Собираем все 8 метрик в один объект и возвращаем контроллеру
        return new AccountantSummaryResponse(
                allOrders.size(), paidOrders, totalRevenue,
                totalClients,
                cartItems.size(), potentialRevenue,
                emailsSent, emailsSuccess
        );
    }

    @Transactional(readOnly = true)
    public List<OrderReportDto> getOrdersReport() {
        // Sort.by(DESC, "orderDate") — сортировка по полю Order.orderDate от новых к старым.
        // Передаём Sort в репозиторий: Spring Data добавит ORDER BY order_date DESC к SQL.
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate")).stream()
                .map(o -> {
                    User u = o.getUser(); // навигация по связи ManyToOne → User через JOIN
                    // Если fullName пуст или null — используем email как отображаемое имя
                    String name = (u.getFullName() != null && !u.getFullName().isBlank())
                            ? u.getFullName() : u.getEmail();
                    return new OrderReportDto(
                            o.getId(), name, u.getEmail(),
                            o.getOrderDate(),
                            o.getStatus().name(),        // Enum → String: "PAID", "CREATED" и т.д.
                            o.getTotalAmount(),
                            o.getItems().size(),         // размер коллекции OrderItem
                            o.getShippingAddress()
                    );
                })
                .toList(); // создаёт неизменяемый список (Java 16+)
    }

    @Transactional(readOnly = true)
    public List<CartReportDto> getCartsReport() {
        return cartItemRepository.findAll().stream()
                .map(ci -> {
                    // Навигация по цепочке JPA-связей: CartItem → Cart → User
                    // Все связи EAGER или загружаются в рамках @Transactional-сессии
                    User u = ci.getCart().getUser();
                    String name = (u.getFullName() != null && !u.getFullName().isBlank())
                            ? u.getFullName() : u.getEmail();
                    // Стоимость позиции = актуальная цена × количество в корзине
                    BigDecimal sub = ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                    return new CartReportDto(
                            name, u.getEmail(),
                            ci.getProduct().getName(),
                            ci.getProduct().getCategory() != null ? ci.getProduct().getCategory().getName() : null,
                            ci.getQuantity(),
                            ci.getProduct().getPrice(), sub
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerReportDto> getCustomersReport() {
        // Для каждого покупателя делаем отдельный запрос его заказов.
        // Это паттерн N+1: 1 запрос за клиентами + N запросов за их заказами.
        // Для небольшой клиентской базы (отчёты) это приемлемо.
        return userRepository.findByRole(Role.CLIENT).stream()
                .map(client -> {
                    // findByUser — SELECT * FROM orders WHERE user_id = ?
                    List<Order> orders = orderRepository.findByUser(client);

                    // В поле totalSpent идут только оплаченные заказы — реально потраченные деньги
                    BigDecimal totalSpent = orders.stream()
                            .filter(o -> o.getStatus() == OrderStatus.PAID)
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    String name = (client.getFullName() != null && !client.getFullName().isBlank())
                            ? client.getFullName() : client.getEmail();

                    return new CustomerReportDto(
                            client.getId(), name, client.getEmail(),
                            orders.size(),       // всего заказов (любой статус) — показатель активности
                            totalSpent,
                            client.getCreatedAt() // дата регистрации пользователя
                    );
                })
                .toList();
    }

    /**
     * Сводка по BNPL-рассрочкам (интеграция с Альфа Банком).
     * Суммы агрегируются в памяти (как и остальные отчёты) и переводятся из копеек в рубли.
     */
    @Transactional(readOnly = true)
    public AccountantBnplResponse getBnplReport() {
        List<BnplContract> contracts = bnplContractRepository.findAll();

        long active    = contracts.stream().filter(c -> c.getStatus() == BnplContractStatus.ACTIVE).count();
        long completed = contracts.stream().filter(c -> c.getStatus() == BnplContractStatus.COMPLETED).count();
        long awaiting  = contracts.stream().filter(c -> c.getStatus() == BnplContractStatus.AWAITING_PAYMENT).count();
        long cancelled = contracts.stream().filter(c -> c.getStatus() == BnplContractStatus.CANCELLED).count();

        // «Действующие» рассрочки (исполняемые/исполненные) — по ним считаем финансовые показатели.
        java.util.function.Predicate<BnplContract> live = c ->
                c.getStatus() == BnplContractStatus.ACTIVE || c.getStatus() == BnplContractStatus.COMPLETED;

        long financedK   = contracts.stream().filter(live).mapToLong(c -> nz(c.getTotalAmountKopecks())).sum();
        long commissionK = contracts.stream().filter(live).mapToLong(c -> nz(c.getCommissionKopecks())).sum();
        long receivedK   = contracts.stream().filter(live).mapToLong(c -> nz(c.getDepositedAmountKopecks())).sum();
        // Остаток к получению — только по активным (по завершённым уже всё получено).
        long outstandingK = contracts.stream()
                .filter(c -> c.getStatus() == BnplContractStatus.ACTIVE)
                .mapToLong(c -> Math.max(0L, nz(c.getTotalAmountKopecks()) - nz(c.getDepositedAmountKopecks())))
                .sum();

        // Журнал фактических списаний по методам (FIRST — первый взнос, SCHEDULED — планировщик, MANUAL — досрочно/админ).
        List<BnplPayment> payments = bnplPaymentRepository.findAll();
        long paymentsTotalK = payments.stream().mapToLong(p -> nz(p.getAmountKopecks())).sum();

        return new AccountantBnplResponse(
                active, completed, awaiting, cancelled,
                rub(financedK), rub(commissionK), rub(receivedK), rub(outstandingK),
                payments.size(), rub(paymentsTotalK),
                methodCount(payments, "FIRST"),     rub(methodSum(payments, "FIRST")),
                methodCount(payments, "SCHEDULED"), rub(methodSum(payments, "SCHEDULED")),
                methodCount(payments, "MANUAL"),    rub(methodSum(payments, "MANUAL"))
        );
    }

    private static long nz(Long v) { return v == null ? 0L : v; }

    private static BigDecimal rub(long kopecks) { return BigDecimal.valueOf(kopecks).movePointLeft(2); }

    private static long methodCount(List<BnplPayment> payments, String method) {
        return payments.stream().filter(p -> method.equals(p.getMethod())).count();
    }

    private static long methodSum(List<BnplPayment> payments, String method) {
        return payments.stream().filter(p -> method.equals(p.getMethod())).mapToLong(p -> nz(p.getAmountKopecks())).sum();
    }

    @Transactional(readOnly = true)
    public List<EmailLogDto> getEmailsReport() {
        // Sort.by(DESC, "sentAt") — свежие попытки отправки будут первыми в списке
        return emailLogRepository.findAll(Sort.by(Sort.Direction.DESC, "sentAt")).stream()
                // Маппинг каждой сущности EmailLog в лёгкий DTO для HTTP-ответа
                .map(el -> new EmailLogDto(
                        el.getId(), el.getRecipient(), el.getSubject(),
                        el.getSentAt(), el.isSuccess(), el.getErrorMessage()
                ))
                .toList();
    }
}
