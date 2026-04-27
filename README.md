# Pet_Ozon — Marketplace REST API

Учебный pet-проект: REST API маркетплейса на Spring Boot 3.4.4.  
Покупатели просматривают товары, добавляют в корзину и оформляют заказы.  
Продавцы управляют своими товарами и видят заработок.  
Администратор управляет всем каталогом и заказами.

> Цель проекта — освоить Spring Boot, JPA, Spring Security, JWT, тестирование.  
> Каждый раздел этого README объясняет не только **что** сделано, но и **почему** именно так.

---

## Содержание

- [Технологический стек](#технологический-стек)
- [Быстрый старт](#быстрый-старт)
- [Тестовые данные](#тестовые-данные)
- [Архитектура приложения](#архитектура-приложения)
- [База данных и сущности](#база-данных-и-сущности)
- [Репозитории — Spring Data JPA](#репозитории--spring-data-jpa)
- [Сервисный слой — бизнес-логика](#сервисный-слой--бизнес-логика)
- [Контроллеры — REST API](#контроллеры--rest-api)
- [DTO — объекты передачи данных](#dto--объекты-передачи-данных)
- [Spring Security и JWT](#spring-security-и-jwt)
- [Обработка ошибок](#обработка-ошибок)
- [Конфигурация](#конфигурация)
- [Тесты](#тесты)
- [Полный сценарий покупки](#полный-сценарий-покупки)
- [API Reference](#api-reference)
- [Запуск в GitLab CI/CD](#запуск-в-gitlab-cicd)

---

## Технологический стек

| Компонент | Версия | Зачем используется |
|---|---|---|
| **Java 21** | 21 (Oracle HotSpot) | Актуальная LTS-версия, поддерживает records, sealed classes |
| **Gradle** | 9.1.0 | Система сборки, управляет зависимостями и запуском |
| **Spring Boot** | 3.4.4 | Автоконфигурация, встроенный Tomcat, BOM зависимостей |
| **Spring Data JPA** | BOM | ORM поверх Hibernate, репозитории генерируются автоматически |
| **Spring Security** | BOM | Аутентификация и авторизация, фильтры HTTP-запросов |
| **Spring Validation** | BOM | Валидация DTO через аннотации (`@NotBlank`, `@Min` и др.) |
| **PostgreSQL** | 15+ | Реляционная СУБД для хранения всех данных |
| **Lombok** | BOM | Генерирует `getters/setters/constructors` через аннотации |
| **jjwt** | 0.12.6 | Создание и валидация JWT-токенов |
| **JUnit 5 + Mockito** | BOM | Юнит-тесты с мокированием зависимостей |
| **Docker / Compose** | любая | Контейнеризация приложения и базы данных |

### Что такое Spring Boot BOM

BOM (Bill of Materials) — это список зависимостей с заранее проверенными совместимыми версиями.  
Когда вы пишете `spring-boot-starter-data-jpa` без версии — Gradle берёт версию из BOM Spring Boot.  
Это гарантирует, что все компоненты Spring работают вместе без конфликтов.

```groovy
// build.gradle — BOM подключается через плагин Spring Boot
plugins {
    id 'org.springframework.boot' version '3.4.4'
    id 'io.spring.dependency-management' version '1.1.7'
}
// После этого можно не указывать версии Spring-зависимостей:
implementation 'org.springframework.boot:spring-boot-starter-data-jpa' // версия берётся из BOM
```

---

## Быстрый старт

### Запуск через Docker (рекомендуется)

Docker соберёт JAR внутри контейнера и поднимет PostgreSQL автоматически.

```bash
docker compose up --build
```

API доступен на `http://localhost:8888`.

```bash
# Остановить и удалить контейнеры
docker compose down

# Только база данных (приложение запускать из IDE)
docker compose up postgres
```

### Запуск локально

**Требования:** Java 21, PostgreSQL 15+.

```sql
-- Создайте базу данных (один раз)
CREATE DATABASE marketplace;
```

```bash
# Запустить приложение
./gradlew bootRun
```

Hibernate автоматически создаст все таблицы (`spring.jpa.hibernate.ddl-auto=update`).  
`AppConfig` наполнит базу тестовыми данными при первом старте.

### Настройки подключения

Файл `src/main/resources/application.properties`:

```properties
# Подключение к PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/marketplace
spring.datasource.username=postgres
spring.datasource.password=1234

# Hibernate создаёт/обновляет таблицы автоматически
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Вывод SQL-запросов в консоль (удобно для обучения)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Порт сервера
server.port=8888

# JWT — секрет и время жизни токена (24 часа)
jwt.secret=<base64-encoded-key>
jwt.expiration=86400000
```

---

## Тестовые данные

`AppConfig.java` — это `CommandLineRunner`, который выполняется **один раз при старте** приложения.  
Он проверяет наличие данных в БД и создаёт тестовых пользователей и товары, если их ещё нет.

### Пользователи

| id | Email | Пароль | Роль | Магазин |
|---|---|---|---|---|
| 1 | client@example.com | pass | CLIENT | — |
| 2 | admin@example.com | pass | ADMIN | — |
| 3 | seller1@example.com | pass | SELLER | TechShop |
| 4 | seller2@example.com | pass | SELLER | AudioWorld |

### Товары

**TechShop** (id=3) — 10 товаров: ноутбуки, мониторы, мышки, клавиатуры, SSD, веб-камеры.  
**AudioWorld** (id=4) — 10 товаров: наушники, колонки, микрофоны, аудиоинтерфейсы.  
Цены: от 2 999 до 119 999 рублей. Остатки: от 5 до 50 единиц.

---

## Архитектура приложения

### Слои и поток данных

```
HTTP-запрос
     │
     ▼
[JwtAuthenticationFilter]   ← проверяет токен, устанавливает аутентификацию
     │
     ▼
[Controller]                ← принимает HTTP, валидирует DTO, вызывает сервис
     │
     ▼
[Service]                   ← бизнес-логика, @Transactional, оркестрация
     │
     ▼
[Repository]                ← Spring Data JPA, SQL-запросы к БД
     │
     ▼
[Entity / PostgreSQL]       ← таблицы базы данных
```

Каждый слой имеет одну ответственность:
- **Controller** — знает про HTTP (статусы, заголовки, URL)
- **Service** — знает про бизнес-правила (нельзя оплатить дважды, нельзя купить больше чем на складе)
- **Repository** — знает про БД (SQL, транзакции, индексы)

### Структура пакетов

```
com.example.marketplace
├── MarketplaceApplication.java     ← точка входа (@SpringBootApplication)
│
├── config/
│   ├── AppConfig.java              ← CommandLineRunner, тестовые данные
│   ├── CorsConfig.java             ← разрешает запросы с фронтенда
│   └── SecurityConfig.java         ← настройки Spring Security, правила доступа
│
├── controller/
│   ├── AuthController              POST /api/auth/login, /api/auth/register
│   ├── ProductController           GET  /api/products, /api/products/{id}
│   ├── CartController              GET/POST /api/cart, /add, /remove, /update, /checkout
│   ├── OrderController             GET  /api/orders/my, /api/orders/{id}
│   ├── InvoiceController           GET  /api/invoice/{id}; POST /{id}/pay
│   ├── ProfileController           GET/PATCH /api/profile/me
│   ├── SellerController            /api/seller/products, /balance, /sales
│   └── AdminController             /api/admin/products, /orders, /invoices
│
├── service/
│   ├── UserService                 ← CRUD пользователей, регистрация
│   ├── ProductService              ← каталог товаров, фильтрация
│   ├── CartService                 ← корзина, checkout (самый сложный сервис)
│   ├── OrderService                ← чтение и управление заказами
│   ├── InvoiceService              ← оплата, начисление выручки продавцам
│   ├── SellerService               ← операции продавца (CRUD его товаров)
│   └── PaymentService              ← заглушка (делегирует в InvoiceService)
│
├── repository/                     ← 8 JpaRepository-интерфейсов
│   ├── UserRepository
│   ├── ProductRepository           ← + JpaSpecificationExecutor (динамические запросы)
│   ├── CartRepository
│   ├── CartItemRepository
│   ├── OrderRepository             ← кастомный @Query для запросов продавца
│   ├── OrderItemRepository
│   ├── InvoiceRepository
│   └── PaymentRepository
│
├── entity/
│   ├── User.java                   ← таблица users, implements UserDetails
│   ├── Product.java                ← таблица products
│   ├── Cart.java                   ← таблица carts (1:1 с User)
│   ├── CartItem.java               ← таблица cart_items (N:1 Cart, N:1 Product)
│   ├── Order.java                  ← таблица orders
│   ├── OrderItem.java              ← таблица order_items (snapshot цены)
│   ├── Invoice.java                ← таблица invoices (1:1 с Order)
│   ├── Payment.java                ← таблица payments
│   └── enums/
│       ├── Role                    CLIENT | SELLER | ADMIN
│       ├── OrderStatus             CREATED | PAID | DELIVERED | CANCELLED
│       ├── InvoiceStatus           UNPAID | PAID | FAILED
│       └── PaymentStatus           SUCCESS | FAILED
│
├── dto/
│   ├── request/                    ← входящие данные от клиента
│   └── response/                   ← исходящие данные клиенту
│
├── security/
│   ├── JwtUtil.java                ← генерация и валидация JWT
│   ├── JwtAuthenticationFilter.java ← фильтр Spring Security
│   └── UserDetailsServiceImpl.java  ← загрузка пользователя по email
│
└── exception/
    ├── ResourceNotFoundException   ← бросается когда сущность не найдена
    └── GlobalExceptionHandler      ← @RestControllerAdvice, перехватывает все ошибки
```

### Связи между сущностями

```
User ──1:1──► Cart ──1:N──► CartItem ──N:1──► Product
 │
 └──1:N──► Order ──1:N──► OrderItem ──N:1──► Product
                │
                └──1:1──► Invoice ──1:N──► Payment

Product ──N:1──► User (seller)
```

Объяснение:
- Каждый пользователь имеет **одну корзину** (1:1)
- В корзине может быть **много позиций** (1:N), каждая ссылается на товар
- Из корзины создаётся **заказ** с позициями (snapshot цен)
- К каждому заказу создаётся **один счёт** (1:1)
- Счёт может иметь **несколько попыток оплаты** (1:N)
- Каждый товар принадлежит **одному продавцу** (N:1)

---

## База данных и сущности

### Что такое JPA Entity

`@Entity` — аннотация, которая говорит Hibernate: «этот Java-класс соответствует таблице в БД».  
Hibernate автоматически генерирует SQL `CREATE TABLE` при старте (если `ddl-auto=update`).

### User.java — таблица `users`

```java
@Entity
@Table(name = "users")
@Data               // Lombok: генерирует getters, setters, toString, equals, hashCode
@NoArgsConstructor  // Lombok: конструктор без аргументов (нужен JPA)
@AllArgsConstructor // Lombok: конструктор со всеми аргументами
public class User implements UserDetails {  // UserDetails — интерфейс Spring Security

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // автоинкремент PostgreSQL (SERIAL)
    private Long id;

    @Column(unique = true, nullable = false)  // UNIQUE INDEX в БД
    private String email;

    @Column(nullable = false)
    private String password;  // всегда хранится в зашифрованном виде (BCrypt)

    private String fullName;
    private String shopName;   // заполняется только для SELLER
    private String address;

    @Column(precision = 15, scale = 2)
    private BigDecimal balance;  // баланс продавца, начисляется при оплате заказа

    @Enumerated(EnumType.STRING)  // хранить строку "CLIENT", не число 0
    private Role role;            // CLIENT | SELLER | ADMIN

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist  // автоматически вызывается Hibernate перед INSERT
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate   // автоматически вызывается Hibernate перед UPDATE
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // --- Методы интерфейса UserDetails (Spring Security) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security требует список ролей в формате "ROLE_ADMIN", "ROLE_CLIENT"
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;  // в нашей системе "username" — это email
    }

    // Эти методы Spring Security использует для проверки состояния аккаунта
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

**Почему `implements UserDetails`?**  
Spring Security при аутентификации вызывает `UserDetailsService.loadUserByUsername()`.  
Этот метод должен вернуть `UserDetails`. Проще всего — сделать сам `User` реализацией этого интерфейса.

**Почему `@Enumerated(EnumType.STRING)`?**  
По умолчанию JPA хранит enum как число (0, 1, 2). Это опасно — при добавлении нового значения перемешаются все.  
`EnumType.STRING` хранит текст "CLIENT" — надёжно и читаемо в БД.

---

### Product.java — таблица `products`

```java
@Entity
@Table(name = "products")
@Data @NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String imageUrl;
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private int stockQuantity;  // уменьшается при оформлении заказа

    @ManyToOne(fetch = FetchType.LAZY)   // N товаров → 1 продавец
    @JoinColumn(name = "seller_id")      // колонка seller_id в таблице products
    private User seller;                 // null для товаров, созданных админом

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

**`FetchType.LAZY` vs `FetchType.EAGER`:**
- `LAZY` — связь загружается только когда к ней обращаются. `SELECT` по продавцу происходит только при `product.getSeller()`.
- `EAGER` — связь загружается сразу вместе с основным объектом (JOIN в SQL).
- По умолчанию `@ManyToOne` использует `EAGER`. Мы явно ставим `LAZY` для производительности.

---

### Cart.java и CartItem.java

```java
@Entity
@Table(name = "carts")
public class Cart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)  // 1 корзина → 1 пользователь
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @OneToMany(
        mappedBy = "cart",              // поле cart в классе CartItem
        cascade = CascadeType.ALL,      // все операции каскадируются на items
        orphanRemoval = true            // удалить CartItem если убрать из списка
    )
    private List<CartItem> items = new ArrayList<>();

    // ...timestamps...
}
```

```java
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private int quantity;
}
```

**Зачем `CascadeType.ALL` + `orphanRemoval = true`?**  
`CascadeType.ALL`: если сохранить/удалить `Cart`, Hibernate автоматически сохранит/удалит все `CartItem`.  
`orphanRemoval = true`: если убрать `CartItem` из списка `cart.getItems().remove(item)` — Hibernate удалит его из БД.  
Это позволяет очистить корзину одной строкой: `cart.getItems().clear()`.

---

### Order.java и OrderItem.java

```java
@Entity
@Table(name = "orders")
public class Order {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;  // CREATED → PAID → DELIVERED / CANCELLED

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;

    private String shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
```

```java
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;  // СНИМОК цены на момент заказа
}
```

**Зачем `priceAtOrder`?**  
Товар стоил 10 000 ₽ на момент заказа. Продавец поднял цену до 15 000 ₽.  
Исторический заказ должен остаться с ценой 10 000 ₽.  
`priceAtOrder` — это snapshot: мы копируем `product.getPrice()` в момент оформления заказа.

---

### Invoice.java и Payment.java

```java
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)  // 1 заказ → 1 счёт
    private Order order;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;  // UNPAID → PAID / FAILED

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<Payment> payments = new ArrayList<>();
}
```

```java
@Entity
@Table(name = "payments")
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    private String paymentMethod;  // "CARD", "CASH" и т.д.

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;  // SUCCESS | FAILED

    private LocalDateTime timestamp;
}
```

**Зачем Invoice и Payment — разные сущности?**  
Invoice — это **запрос на оплату** (сколько нужно заплатить).  
Payment — это **попытка оплаты** (конкретная транзакция).  
Один счёт может иметь несколько попыток: первая — FAILED, вторая — SUCCESS.

---

## Репозитории — Spring Data JPA

**Что такое JpaRepository?**  
Это интерфейс Spring Data. Когда вы объявляете `interface UserRepository extends JpaRepository<User, Long>`,  
Spring автоматически генерирует реализацию с методами: `save()`, `findById()`, `findAll()`, `delete()` и т.д.  
Не нужно писать SQL или DAO-классы вручную.

### UserRepository

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    // Spring Data генерирует SQL: SELECT * FROM users WHERE email = ?
    // Optional<> означает: может вернуть null — явно обработай этот случай

    boolean existsByEmail(String email);
    // SELECT COUNT(*) > 0 FROM users WHERE email = ?
    // Используется при регистрации: проверить, не занят ли email
}
```

**Что такое `Optional<T>`?**  
`Optional` — обёртка вокруг значения, которое может отсутствовать.  
Вместо `if (user != null)` пишем: `userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"))`.

---

### ProductRepository

```java
public interface ProductRepository
    extends JpaRepository<Product, Long>,
            JpaSpecificationExecutor<Product> {  // нужен для динамических фильтров

    List<Product> findBySeller(User seller);
    // SELECT * FROM products WHERE seller_id = ?

    Page<Product> findAll(Pageable pageable);
    // SELECT * FROM products LIMIT ? OFFSET ?
}
```

**Что такое `JpaSpecificationExecutor`?**  
Позволяет строить динамические SQL-запросы через Java-код (паттерн Specification).  
Например, фильтр товаров: если передали `name` — добавить `WHERE name LIKE ?`,  
если передали `minPrice` — добавить `AND price >= ?`.

```java
// Пример из ProductService — динамические фильтры
List<Specification<Product>> specs = new ArrayList<>();

if (name != null && !name.isBlank()) {
    specs.add((root, query, cb) ->
        cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
    // Генерирует: WHERE LOWER(name) LIKE '%ноутбук%'
}

if (minPrice != null) {
    specs.add((root, query, cb) ->
        cb.greaterThanOrEqualTo(root.get("price"), minPrice));
    // Генерирует: AND price >= 10000
}

// Объединяем все условия через AND:
Specification<Product> combined = specs.stream()
    .reduce(Specification::and)
    .orElse(null);

return productRepository.findAll(combined, pageable);
```

---

### OrderRepository

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);
    Page<Order> findByUser(User user, Pageable pageable);

    // Кастомный JPQL-запрос — найти заказы, содержащие товары конкретного продавца
    @Query("SELECT DISTINCT oi.order FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    List<Order> findBySellerId(@Param("sellerId") Long sellerId);
    // JPQL: язык запросов Spring Data, работает с именами классов/полей, а не таблиц/колонок
    // DISTINCT: один заказ может содержать несколько товаров продавца — берём без дублей
}
```

---

### Пагинация — Pageable

`Pageable` позволяет передать в запрос номер страницы, размер и сортировку.

```
GET /api/products?page=0&size=10&sort=price,asc
                  ↓
Pageable pageable = PageRequest.of(0, 10, Sort.by("price").ascending())
                  ↓
SELECT * FROM products ORDER BY price ASC LIMIT 10 OFFSET 0
```

Ответ — объект `Page<T>`, содержащий: список элементов, общее число страниц, текущую страницу.

---

## Сервисный слой — бизнес-логика

### Что такое `@Transactional`

`@Transactional` означает: все операции с БД внутри метода — одна транзакция.  
Если в середине метода бросается исключение — все изменения **откатываются**.  
Это гарантирует согласованность данных.

Пример: при `checkout()` нужно создать Order + OrderItems + Invoice + очистить корзину.  
Если что-то упадёт на 3-м шаге — ни Order, ни CartItems не останутся в БД «наполовину».

---

### UserService

```java
@Service  // пометить как Spring-компонент, чтобы можно было @Autowired / @RequiredArgsConstructor
@Slf4j    // Lombok: создаёт logger log = LoggerFactory.getLogger(UserService.class)
@RequiredArgsConstructor  // Lombok: конструктор для final-полей (инъекция зависимостей)
public class UserService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        // Optional.orElseThrow(): если нет значения — бросить исключение
    }

    @Transactional
    public User registerClient(String email, String password, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already taken: " + email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));  // BCrypt хеширование
        user.setFullName(fullName);
        user.setRole(Role.CLIENT);
        user.setBalance(BigDecimal.ZERO);
        User saved = userRepository.save(user);

        // Автоматически создаём корзину для нового клиента
        Cart cart = new Cart();
        cart.setUser(saved);
        cartRepository.save(cart);

        log.info("Registered new client: {}", email);
        return saved;
    }
}
```

**Почему инъекция через конструктор, а не `@Autowired` на поле?**  
`@RequiredArgsConstructor` генерирует конструктор с `final`-полями.  
Spring видит единственный конструктор и сам передаёт зависимости.  
Это лучше, чем `@Autowired` на поле: легче тестировать (можно передать mock), поля immutable.

---

### CartService — самый сложный сервис

#### addToCart()

```java
@Transactional
public CartResponse addToCart(Long userId, Long productId, int quantity) {
    User user = userService.getById(userId);
    Cart cart = cartRepository.findByUser(user)
        .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

    // Проверяем — есть ли уже этот товар в корзине
    Optional<CartItem> existingItem = cartItemRepository.findByCartAndProduct(cart, product);

    if (existingItem.isPresent()) {
        // Товар уже есть — увеличиваем количество
        existingItem.get().setQuantity(existingItem.get().getQuantity() + quantity);
        cartItemRepository.save(existingItem.get());
    } else {
        // Товара нет — создаём новую позицию
        CartItem newItem = new CartItem();
        newItem.setCart(cart);
        newItem.setProduct(product);
        newItem.setQuantity(quantity);
        cartItemRepository.save(newItem);
    }

    return toResponse(cartRepository.findByUser(user).orElseThrow());
}
```

#### checkout() — главный метод

```java
@Transactional
public OrderResponse checkout(Long userId, String shippingAddress) {
    User user = userService.getById(userId);
    Cart cart = cartRepository.findByUser(user).orElseThrow();

    if (cart.getItems().isEmpty()) {
        throw new IllegalArgumentException("Cart is empty");
    }

    // ШАГ 1: Проверка наличия товаров на складе
    for (CartItem item : cart.getItems()) {
        if (item.getProduct().getStockQuantity() < item.getQuantity()) {
            throw new IllegalArgumentException(
                "Not enough stock for: " + item.getProduct().getName());
        }
    }

    // ШАГ 2: Создать заказ
    Order order = new Order();
    order.setUser(user);
    order.setOrderDate(LocalDateTime.now());
    order.setStatus(OrderStatus.CREATED);
    order.setShippingAddress(shippingAddress);

    // ШАГ 3: Создать позиции заказа с snapshot-ценами и посчитать сумму
    BigDecimal total = BigDecimal.ZERO;
    List<OrderItem> orderItems = new ArrayList<>();

    for (CartItem cartItem : cart.getItems()) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(cartItem.getProduct());
        orderItem.setQuantity(cartItem.getQuantity());
        orderItem.setPriceAtOrder(cartItem.getProduct().getPrice());  // snapshot!

        BigDecimal itemTotal = cartItem.getProduct().getPrice()
            .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        total = total.add(itemTotal);

        orderItems.add(orderItem);
    }
    order.setTotalAmount(total);
    order.setItems(orderItems);
    Order savedOrder = orderRepository.save(order);  // CascadeType.ALL сохранит и OrderItems

    // ШАГ 4: Уменьшить остаток на складе
    for (CartItem item : cart.getItems()) {
        Product product = item.getProduct();
        product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
        productRepository.save(product);
    }

    // ШАГ 5: Создать счёт
    Invoice invoice = new Invoice();
    invoice.setOrder(savedOrder);
    invoice.setAmount(total);
    invoice.setStatus(InvoiceStatus.UNPAID);
    invoice.setCreatedAt(LocalDateTime.now());
    invoiceRepository.save(invoice);

    // ШАГ 6: Очистить корзину
    cart.getItems().clear();  // orphanRemoval=true удалит CartItems из БД
    cartRepository.save(cart);

    log.info("Order created: id={}, total={}", savedOrder.getId(), total);
    return orderService.toResponse(savedOrder);
}
```

---

### InvoiceService — оплата и начисление выручки

```java
@Transactional
public PaymentResponse pay(Long invoiceId, String paymentMethod) {
    Invoice invoice = invoiceRepository.findById(invoiceId)
        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

    // Защита от двойной оплаты
    if (invoice.getStatus() == InvoiceStatus.PAID) {
        throw new IllegalArgumentException("Invoice already paid: " + invoiceId);
    }

    // Обновить статусы
    invoice.setStatus(InvoiceStatus.PAID);
    invoice.setPaidAt(LocalDateTime.now());
    invoiceRepository.save(invoice);

    Order order = invoice.getOrder();
    order.setStatus(OrderStatus.PAID);
    orderRepository.save(order);

    // Начислить выручку каждому продавцу за его товары в заказе
    for (OrderItem item : order.getItems()) {
        User seller = item.getProduct().getSeller();
        if (seller != null) {
            BigDecimal earnings = item.getPriceAtOrder()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
            seller.setBalance(seller.getBalance().add(earnings));
            userRepository.save(seller);
        }
    }

    // Записать транзакцию
    Payment payment = new Payment();
    payment.setInvoice(invoice);
    payment.setAmount(invoice.getAmount());
    payment.setPaymentMethod(paymentMethod != null ? paymentMethod : "CARD");
    payment.setStatus(PaymentStatus.SUCCESS);
    payment.setTimestamp(LocalDateTime.now());
    Payment saved = paymentRepository.save(payment);

    return toResponse(saved);
}
```

---

### SellerService — изоляция данных продавца

```java
@Transactional
public ProductResponse updateProduct(Long sellerId, Long productId, CreateProductRequest req) {
    User seller = resolveSeller(sellerId);  // проверяет что пользователь — SELLER

    // resolveSellerProduct проверяет, что товар принадлежит именно этому продавцу
    Product product = resolveSellerProduct(seller, productId);

    product.setName(req.getName());
    product.setDescription(req.getDescription());
    product.setPrice(req.getPrice());
    product.setStockQuantity(req.getStockQuantity());
    product.setImageUrl(req.getImageUrl());
    product.setCategory(req.getCategory());

    return productService.toResponse(productRepository.save(product));
}

private User resolveSeller(Long sellerId) {
    User user = userService.getById(sellerId);
    if (user.getRole() != Role.SELLER) {
        throw new IllegalArgumentException("User is not a seller: " + sellerId);
    }
    return user;
}

private Product resolveSellerProduct(User seller, Long productId) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    if (!product.getSeller().getId().equals(seller.getId())) {
        throw new IllegalArgumentException("Product does not belong to this seller");
    }
    return product;
}
```

---

## Контроллеры — REST API

### Основные аннотации контроллеров

| Аннотация | Что делает |
|---|---|
| `@RestController` | = `@Controller` + `@ResponseBody`. Каждый метод возвращает JSON |
| `@RequestMapping("/api/cart")` | Базовый URL для всех методов контроллера |
| `@GetMapping("/{id}")` | Обрабатывает `GET /api/cart/{id}` |
| `@PostMapping("/add")` | Обрабатывает `POST /api/cart/add` |
| `@PutMapping("/{id}")` | Обрабатывает `PUT /api/cart/{id}` |
| `@DeleteMapping("/{id}")` | Обрабатывает `DELETE /api/cart/{id}` |
| `@PathVariable Long id` | Извлечь `{id}` из URL |
| `@RequestBody @Valid AddToCartRequest req` | Прочитать JSON из тела запроса и провалидировать |
| `@RequestParam(required=false) String name` | Параметр URL `?name=ноутбук` |
| `@AuthenticationPrincipal User user` | Получить текущего аутентифицированного пользователя |
| `ResponseEntity<T>` | Обёртка для явного указания HTTP-статуса ответа |

---

### AuthController — вход и регистрация

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest req) {
        // 1. Проверяем email + password через Spring Security
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        // Если пароль неверный — AuthenticationManager бросит AuthenticationException → 401

        // 2. Загружаем полный объект пользователя
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.getEmail());

        // 3. Генерируем JWT-токен
        String token = jwtUtil.generateToken(userDetails);

        User user = (User) userDetails;
        AuthResponse body = new AuthResponse(token, user.getId(), user.getEmail(),
            user.getRole(), user.getFullName(), user.getShopName());

        return ResponseEntity.ok()
            .header("Authorization", "Bearer " + token)  // токен в заголовке
            .body(body);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest req) {
        User user = userService.registerClient(req.getEmail(), req.getPassword(), req.getFullName());
        String token = jwtUtil.generateToken(user);

        AuthResponse body = new AuthResponse(token, user.getId(), user.getEmail(),
            user.getRole(), user.getFullName(), user.getShopName());

        return ResponseEntity.status(HttpStatus.CREATED)
            .header("Authorization", "Bearer " + token)
            .body(body);
    }
}
```

---

### CartController

```java
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal User user) {
        // @AuthenticationPrincipal берёт объект из SecurityContext
        // (туда его поместил JwtAuthenticationFilter)
        return ResponseEntity.ok(cartService.getCartByUserId(user.getId()));
    }

    @PostMapping("/add")
    public ResponseEntity<CartResponse> addToCart(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid AddToCartRequest req) {
        return ResponseEntity.ok(
            cartService.addToCart(user.getId(), req.getProductId(), req.getQuantity()));
    }

    @DeleteMapping("/remove/{cartItemId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/update/{cartItemId}")
    public ResponseEntity<CartResponse> updateQuantity(
            @AuthenticationPrincipal User user,
            @PathVariable Long cartItemId,
            @RequestBody @Valid UpdateCartItemRequest req) {
        return ResponseEntity.ok(
            cartService.updateQuantity(cartItemId, req.getQuantity()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid CheckoutRequest req) {
        return ResponseEntity.ok(
            cartService.checkout(user.getId(), req.getShippingAddress()));
    }
}
```

---

### AdminController — доступ только для ADMIN

```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;

    // Доступ к этим методам разрешён только пользователям с ролью ADMIN
    // — это настраивается в SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(req));
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateOrderStatusRequest req) {
        return ResponseEntity.ok(orderService.updateStatus(id, req.getStatus()));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }
}
```

---

## DTO — объекты передачи данных

**Зачем нужны DTO?**  
Entity-классы напрямую отдавать клиенту опасно:
1. Entity `User` содержит `password` — его нельзя показывать
2. Циклические ссылки: `Order` → `User` → `List<Order>` → бесконечный JSON
3. Внутренняя структура БД не должна быть частью публичного API

DTO — это простые Java-классы только с нужными полями для API.

### Request DTO с валидацией

```java
// dto/request/CreateProductRequest.java
@Data
public class CreateProductRequest {

    @NotBlank(message = "Name is required")  // поле обязательно, не пустая строка
    private String name;

    private String description;  // опциональное поле

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")  // цена > 0
    private BigDecimal price;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private int stockQuantity;

    private String imageUrl;
    private String category;
}
```

```java
// dto/request/RegisterRequest.java
@Data
public class RegisterRequest {

    @NotBlank
    @Email(message = "Invalid email format")  // проверяет формат email@domain.com
    private String email;

    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String fullName;
}
```

Аннотации проверяются когда в контроллере написано `@Valid`:
```java
public ResponseEntity<?> create(@RequestBody @Valid CreateProductRequest req) {
    // если поле price = -100, Spring выбрасывает MethodArgumentNotValidException
    // GlobalExceptionHandler превращает это в 400 с описанием ошибки
}
```

### Response DTO

```java
// dto/response/CartResponse.java
@Data
@AllArgsConstructor
public class CartResponse {
    private Long id;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;  // считается в CartService, не хранится в БД
}

// dto/response/OrderResponse.java
@Data
public class OrderResponse {
    private Long id;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private List<OrderItemResponse> items;
    private Long invoiceId;  // чтобы клиент знал, по какому счёту платить
}
```

---

## Spring Security и JWT

### Как это работает в целом

```
1. POST /api/auth/login {email, password}
   → AuthController.login()
   → AuthenticationManager проверяет пароль
   → JwtUtil генерирует token
   → Возвращает {"token": "eyJhbGci..."}

2. GET /api/cart   Authorization: Bearer eyJhbGci...
   → JwtAuthenticationFilter.doFilterInternal()
   → Извлекает токен из заголовка
   → JwtUtil.extractUsername(token) → "user@email.com"
   → Загружает User из БД
   → JwtUtil.isTokenValid() → true
   → Помещает User в SecurityContext
   → Запрос доходит до CartController
   → @AuthenticationPrincipal User user — получаем User из SecurityContext
```

---

### JwtUtil.java

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")  // читает из application.properties
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;  // 86400000 мс = 24 часа

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
            .subject(userDetails.getUsername())  // "sub": "user@email.com"
            .issuedAt(new Date())                // "iat": текущее время
            .expiration(new Date(System.currentTimeMillis() + expiration))  // "exp": через 24 часа
            .signWith(signingKey())              // подпись HMAC-SHA256
            .compact();                          // собрать в строку вида xxxxx.yyyyy.zzzzz
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
        // Декодирует токен, проверяет подпись, возвращает поле "sub"
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return resolver.apply(claims);
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);  // HMAC-SHA256 ключ
    }
}
```

**Как устроен JWT-токен:**
```
eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiJ1c2VyQGV4LmNvbSIsImlhdCI6MTcxNzAwMDAwMCwiZXhwIjoxNzE3MDg2NDAwfQ  .  <подпись>
      header                                       payload                                                         signature
(алгоритм подписи)           (base64: {"sub":"user@ex.com","iat":1717000000,"exp":1717086400})
```

Токен **не шифруется** — payload можно прочитать без ключа (base64).  
Токен **подписывается** — изменить payload без ключа нельзя: подпись станет невалидной.

---

### JwtAuthenticationFilter.java

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // OncePerRequestFilter: выполняется ровно один раз на каждый HTTP-запрос

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Если заголовка нет или он не "Bearer ..." — пропускаем
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);  // убираем "Bearer "

        try {
            String username = jwtUtil.extractUsername(token);

            // Если пользователь ещё не аутентифицирован в этом запросе
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    // Создаём объект аутентификации
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,    // principal (наш User)
                            null,           // credentials (пароль не нужен после JWT)
                            userDetails.getAuthorities()  // роли
                        );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Помещаем в SecurityContext — теперь Spring знает кто делает запрос
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            // Невалидный токен — просто продолжаем как анонимный запрос
        }

        filterChain.doFilter(request, response);  // передаём дальше по цепочке
    }
}
```

---

### SecurityConfig.java — правила доступа

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // REST API не нуждается в CSRF-защите
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // STATELESS: не создавать сессии. Аутентификация только через JWT каждый запрос.

            .authorizeHttpRequests(auth -> auth
                // Публичные эндпоинты — без токена
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                // Только ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // только SELLER
                .requestMatchers("/api/seller/**").hasRole("SELLER")
                // Остальное — любой аутентифицированный пользователь
                .anyRequest().authenticated()
            )

            // Вставить наш JWT-фильтр ПЕРЕД стандартным фильтром логина
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt хеширует пароль с солью: "pass" → "$2a$10$abc123..."
        // При проверке: encoder.matches("pass", "$2a$10$abc123...") → true
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

---

## Обработка ошибок

### GlobalExceptionHandler.java

`@RestControllerAdvice` — перехватывает исключения из всех контроллеров и возвращает JSON.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Наше бизнес-исключение: сущность не найдена
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 404));
    }

    // Нарушение бизнес-правил (корзина пуста, счёт уже оплачен, email занят)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 400));
    }

    // Ошибки валидации @Valid — собираем все поля с ошибками
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        // Например: "price: Price must be positive; name: Name is required"

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(LocalDateTime.now(), message, 400));
    }

    // Ошибки аутентификации (неверный пароль)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 401));
    }

    // Все остальные ошибки
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(LocalDateTime.now(), "Internal server error", 500));
    }
}
```

**Формат ответа об ошибке:**
```json
{
  "timestamp": "2025-04-17T12:00:00",
  "message": "Product not found with id: 99",
  "status": 404
}
```

---

## Конфигурация

### AppConfig.java — тестовые данные при старте

```java
@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        // CommandLineRunner выполняется ОДИН РАЗ после запуска Spring-контекста
        return args -> {
            // Создаём пользователей только если их ещё нет
            if (!userRepository.existsByEmail("client@example.com")) {
                User client = createUser("client@example.com", "Иван Клиентов", null, Role.CLIENT);
                createCartFor(client);
            }
            if (!userRepository.existsByEmail("seller1@example.com")) {
                User seller1 = createUser("seller1@example.com", "Алексей Технов", "TechShop", Role.SELLER);
                createCartFor(seller1);
            }
            // ... и т.д.

            // Товары создаём только если таблица пуста
            if (productRepository.count() == 0) {
                createTechShopProducts(seller1);
                createAudioWorldProducts(seller2);
            }
        };
    }

    private User createUser(String email, String name, String shopName, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("pass"));  // пароль всегда хешируем!
        u.setFullName(name);
        u.setShopName(shopName);
        u.setRole(role);
        u.setBalance(BigDecimal.ZERO);
        return userRepository.save(u);
    }
}
```

---

### CorsConfig.java

CORS (Cross-Origin Resource Sharing) — браузер блокирует запросы с одного домена на другой.  
Если фронтенд на `localhost:3000` обращается к API на `localhost:8888` — это CORS.

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));              // разрешить все домены
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));         // клиент может читать этот заголовок
        config.setMaxAge(3600L);                                    // кешировать preflight 1 час

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);            // применить ко всем URL
        return source;
    }
}
```

---

## Тесты

### Структура тестов

```
src/test/java/com/example/marketplace/
├── service/
│   ├── CartServiceTest.java        8 тестов: addToCart, removeFromCart, checkout
│   ├── InvoiceServiceTest.java     — оплата, начисление баланса продавцу
│   ├── OrderServiceTest.java       — getOrdersByUserId, updateStatus
│   ├── ProductServiceTest.java     — CRUD, динамическая фильтрация
│   ├── SellerServiceTest.java      18 тестов: CRUD товаров продавца, balance, sales
│   └── UserServiceTest.java        — getById, registerClient
└── controller/
    ├── AuthControllerTest.java     — login 200/401, register 201/400
    ├── CartControllerTest.java     — все эндпоинты корзины
    ├── InvoiceControllerTest.java  — getById, pay
    ├── OrderControllerTest.java    — getMyOrders, getById
    ├── ProductControllerTest.java  — getAll с фильтрами, getById
    ├── SellerControllerTest.java   16 тестов: products, balance, sales
    └── AdminControllerTest.java    — products, orders, invoices
```

### Юнит-тесты сервисов

```java
@ExtendWith(MockitoExtension.class)  // инициализировать @Mock поля через Mockito
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;        // мок — "заглушка" репозитория

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;              // CartService с подставленными моками

    @Test
    void addToCart_existingItem_shouldIncreaseQuantity() {
        // GIVEN: настраиваем, что вернут моки
        User user = new User(); user.setId(1L);
        Product product = new Product(); product.setId(1L); product.setPrice(new BigDecimal("100"));
        Cart cart = new Cart(); cart.setUser(user); cart.setItems(new ArrayList<>());
        CartItem existingItem = new CartItem(); existingItem.setProduct(product); existingItem.setQuantity(2);
        cart.getItems().add(existingItem);

        when(userService.getById(1L)).thenReturn(user);
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartAndProduct(cart, product)).thenReturn(Optional.of(existingItem));

        // WHEN: вызываем тестируемый метод
        cartService.addToCart(1L, 1L, 3);

        // THEN: проверяем результат
        assertThat(existingItem.getQuantity()).isEqualTo(5);  // 2 + 3
        verify(cartItemRepository).save(existingItem);        // убеждаемся что save вызван
    }
}
```

**`when(...).thenReturn(...)`** — настроить мок: "когда вызовут этот метод с этими аргументами — вернуть вот это".  
**`verify(mock).method()`** — проверить, что метод был вызван.  
**`assertThat(actual).isEqualTo(expected)`** — AssertJ: более читаемые проверки чем JUnit `assertEquals`.

### Интеграционные тесты контроллеров

```java
@WebMvcTest(CartController.class)  // загружает только этот контроллер, без остального Spring
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;  // имитирует HTTP-запросы без реального сервера

    @MockBean
    private CartService cartService;  // мок для Spring-контекста

    @Test
    void getCart_shouldReturnCart() throws Exception {
        CartResponse response = new CartResponse(1L, List.of(), BigDecimal.ZERO);
        when(cartService.getCartByUserId(1L)).thenReturn(response);

        mockMvc.perform(get("/api/cart")
                .with(user(testUser)))  // имитировать аутентифицированного пользователя
            .andExpect(status().isOk())           // HTTP 200
            .andExpect(jsonPath("$.id").value(1L))  // проверить поле в JSON
            .andExpect(jsonPath("$.items").isEmpty());
    }
}
```

```bash
# Запуск всех тестов
./gradlew test

# HTML-отчёт
build/reports/tests/test/index.html
```

---

## Полный сценарий покупки

Пошаговый flow с объяснением что происходит на каждом уровне:

### 1. Регистрация

```
POST /api/auth/register
{email: "user@ex.com", password: "secret"}
```
```
AuthController.register()
  → UserService.registerClient()
      → userRepository.existsByEmail() → false (можно)
      → passwordEncoder.encode("secret") → "$2a$10$..."
      → userRepository.save(user) → user.id = 5
      → Cart cart = new Cart(user); cartRepository.save(cart)
  → JwtUtil.generateToken(user) → "eyJhbGci..."
← 201 {token, userId:5, email, role:"CLIENT"}
```

### 2. Просмотр каталога

```
GET /api/products?category=Ноутбуки&maxPrice=100000&page=0&size=5
```
```
ProductController.getAllProducts()
  → ProductService.getAllProducts("Ноутбуки", null, null, 100000, PageRequest(0, 5))
      → Specification: WHERE category = 'Ноутбуки' AND price <= 100000
      → productRepository.findAll(spec, pageable)
      → SQL: SELECT * FROM products WHERE category='Ноутбуки' AND price <= 100000 LIMIT 5
      → .map(product -> toResponse(product)) → List<ProductResponse>
← 200 Page{content: [...], totalElements: 3, totalPages: 1}
```

### 3. Добавление в корзину

```
POST /api/cart/add
Authorization: Bearer eyJhbGci...
{productId: 1, quantity: 2}
```
```
JwtAuthenticationFilter:
  → token → username "user@ex.com"
  → userDetailsService.loadUserByUsername() → User{id:5}
  → SecurityContext.setAuthentication(user)

CartController.addToCart(@AuthenticationPrincipal User user)
  → CartService.addToCart(5, 1, 2)
      → cartRepository.findByUser(user) → Cart{id:5, items:[]}
      → productRepository.findById(1) → Product{name:"Ноутбук", price:89999}
      → cartItemRepository.findByCartAndProduct() → Optional.empty() (нет в корзине)
      → cartItemRepository.save(CartItem{product:1, quantity:2})
← 200 {id:5, items:[{productId:1, name:"Ноутбук", quantity:2, price:89999}], totalPrice:179998}
```

### 4. Оформление заказа

```
POST /api/cart/checkout
Authorization: Bearer eyJhbGci...
{shippingAddress: "Москва, ул. Ленина, 1"}
```
```
CartService.checkout(5, "Москва...")  @Transactional — всё или ничего
  1. cart.getItems() → [CartItem{product:1, qty:2}]
  2. product.stockQuantity(10) >= quantity(2) ✓
  3. Order order = new Order(user, CREATED, "Москва...")
  4. OrderItem item = new OrderItem(order, product, 2, priceAtOrder=89999)
  5. total = 89999 × 2 = 179998
  6. orderRepository.save(order) → order.id = 1
     → CascadeType.ALL: автоматически сохраняет OrderItem
  7. product.stockQuantity = 10 - 2 = 8; productRepository.save(product)
  8. Invoice invoice = new Invoice(order, 179998, UNPAID)
     invoiceRepository.save(invoice) → invoice.id = 1
  9. cart.getItems().clear(); cartRepository.save(cart)
     → orphanRemoval: удаляет CartItem из БД
← 200 {id:1, status:"CREATED", total:179998, invoiceId:1, items:[...]}
```

### 5. Оплата

```
POST /api/invoice/1/pay
Authorization: Bearer eyJhbGci...
{paymentMethod: "CARD"}
```
```
InvoiceService.pay(1, "CARD")  @Transactional
  1. invoice.status = UNPAID ✓
  2. invoice.status = PAID; invoice.paidAt = now()
  3. order.status = PAID
  4. Для OrderItem{product{seller:TechShop}, qty:2, price:89999}:
     seller.balance += 89999 × 2 = 179998
     userRepository.save(seller)
  5. Payment payment = new Payment(invoice, 179998, "CARD", SUCCESS)
     paymentRepository.save(payment)
← 200 {id:1, invoiceId:1, amount:179998, paymentMethod:"CARD", status:"SUCCESS"}
```

---

## API Reference

Базовый URL: `http://localhost:8888`

**Аутентификация:** `Authorization: Bearer <JWT-token>`  
Получить токен: `POST /api/auth/login`

**Формат ошибок:**
```json
{"timestamp": "2025-04-17T12:00:00", "message": "...", "status": 404}
```

---

### Аутентификация

| Метод | URL | Тело | Ответ | Доступ |
|---|---|---|---|---|
| POST | `/api/auth/login` | `{email, password}` | `{token, userId, email, role}` + заголовок `Authorization` | Все |
| POST | `/api/auth/register` | `{email, password, fullName?}` | `{token, userId, email, role}` | Все |

---

### Товары

| Метод | URL | Параметры | Ответ | Доступ |
|---|---|---|---|---|
| GET | `/api/products` | `name`, `category`, `minPrice`, `maxPrice`, `page`, `size`, `sort` | `Page<ProductResponse>` | Все |
| GET | `/api/products/{id}` | — | `ProductResponse` | Все |

---

### Профиль

| Метод | URL | Тело | Ответ | Доступ |
|---|---|---|---|---|
| GET | `/api/profile/me` | — | `ProfileResponse` | Авторизованный |
| PATCH | `/api/profile/me` | `{fullName?, address?, shopName?}` | `ProfileResponse` | Авторизованный |

---

### Корзина

| Метод | URL | Тело | Ответ | Доступ |
|---|---|---|---|---|
| GET | `/api/cart` | — | `CartResponse` | Авторизованный |
| POST | `/api/cart/add` | `{productId, quantity}` | `CartResponse` | Авторизованный |
| PUT | `/api/cart/update/{cartItemId}` | `{quantity}` | `CartResponse` | Авторизованный |
| DELETE | `/api/cart/remove/{cartItemId}` | — | 200 | Авторизованный |
| POST | `/api/cart/checkout` | `{shippingAddress}` | `OrderResponse` | Авторизованный |

---

### Заказы

| Метод | URL | Параметры | Ответ | Доступ |
|---|---|---|---|---|
| GET | `/api/orders/my` | `page`, `size` | `Page<OrderResponse>` | Авторизованный |
| GET | `/api/orders/{id}` | — | `OrderResponse` | Авторизованный |

---

### Счета и оплата

| Метод | URL | Тело | Ответ | Доступ |
|---|---|---|---|---|
| GET | `/api/invoice/{id}` | — | `InvoiceResponse` | Авторизованный |
| POST | `/api/invoice/{id}/pay` | `{paymentMethod}` | `PaymentResponse` | Авторизованный |

---

### Продавец (требует роль SELLER)

| Метод | URL | Тело | Ответ |
|---|---|---|---|
| GET | `/api/seller/products` | — | `List<ProductResponse>` |
| POST | `/api/seller/products` | `CreateProductRequest` | `ProductResponse` 201 |
| PUT | `/api/seller/products/{id}` | `CreateProductRequest` | `ProductResponse` |
| DELETE | `/api/seller/products/{id}` | — | 204 |
| GET | `/api/seller/balance` | — | `SellerResponse` |
| GET | `/api/seller/sales` | — | `List<OrderResponse>` |

---

### Администратор (требует роль ADMIN)

| Метод | URL | Тело | Ответ |
|---|---|---|---|
| POST | `/api/admin/products` | `CreateProductRequest` | `ProductResponse` 201 |
| PUT | `/api/admin/products/{id}` | `CreateProductRequest` | `ProductResponse` |
| DELETE | `/api/admin/products/{id}` | — | 204 |
| GET | `/api/admin/orders` | `page`, `size` | `Page<OrderResponse>` |
| PUT | `/api/admin/orders/{id}/status` | `{status}` | `OrderResponse` |
| GET | `/api/admin/invoices` | — | `List<InvoiceResponse>` |

---

## Запуск в GitLab CI/CD

В репозитории есть файл `.gitlab-ci.yml`, который автоматически запускает тесты, собирает JAR и публикует Docker-образ при каждом пуше в GitLab.

### Как работает пайплайн

```
git push → GitLab запускает пайплайн из .gitlab-ci.yml
              │
              ▼
         [test]      ← ./gradlew test (с реальной PostgreSQL в Docker)
              │
              ▼
         [build]     ← ./gradlew bootJar (собирает fat JAR)
              │
              ▼
         [docker]    ← docker build + docker push (только ветка main)
```

Три стадии выполняются **последовательно**: если тесты упали — сборка не запустится.

### Структура `.gitlab-ci.yml`

| Стадия | Что делает | Когда запускается |
|---|---|---|
| `test` | Прогоняет `./gradlew test`, поднимает PostgreSQL как service | Каждый push |
| `build` | Собирает JAR через `./gradlew bootJar -x test` | После успешных тестов |
| `docker` | Собирает и публикует Docker-образ в GitLab Registry | Только в ветке `main` |

### Быстрый старт

**1. Загрузить проект в GitLab**

```bash
# Если репозиторий ещё не создан в GitLab — создайте его через UI
# Затем добавьте remote и запушьте
git remote add origin https://gitlab.com/YOUR_USERNAME/pet-ozon.git
git push -u origin master
```

После пуша GitLab автоматически найдёт `.gitlab-ci.yml` и запустит пайплайн.  
Статус: **CI/CD → Pipelines** в меню слева.

**2. Настроить Runner (если его нет)**

GitLab.com предоставляет бесплатные shared runners — дополнительных настроек не нужно.  
Для self-hosted GitLab нужно [установить и зарегистрировать runner](https://docs.gitlab.com/runner/install/).

**3. Настроить переменные для Docker Registry (опционально)**

Для стадии `docker` нужно чтобы `CI_REGISTRY_IMAGE` было настроено.  
GitLab автоматически выставляет переменные `CI_REGISTRY`, `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD`  
если Container Registry включён для проекта (**Settings → General → Visibility → Container Registry**).

Вручную добавить переменные: **Settings → CI/CD → Variables**:

| Переменная | Пример значения | Описание |
|---|---|---|
| `CI_REGISTRY_IMAGE` | `registry.gitlab.com/username/pet-ozon` | Полный путь к образу |

### Просмотр результатов тестов

GitLab автоматически показывает результаты тестов прямо в Merge Request:

```
Merge Request → вкладка "Tests"
```

Полный HTML-отчёт Gradle:

```
CI/CD → Pipelines → выбрать запуск → Jobs → test → Browse Artifacts → build/reports/tests/test/index.html
```

### Запуск образа из Registry

После успешной стадии `docker` образ доступен в GitLab Registry:

```bash
# Скачать образ
docker pull registry.gitlab.com/YOUR_USERNAME/pet-ozon:latest

# Запустить с внешней БД
docker run -p 8888:8888 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/marketplace \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=1234 \
  registry.gitlab.com/YOUR_USERNAME/pet-ozon:latest
```

### Кеш зависимостей Gradle

Пайплайн кеширует скачанные зависимости Gradle между запусками.  
Ключ кеша — содержимое файлов `build.gradle` и `settings.gradle`.  
При изменении зависимостей кеш автоматически инвалидируется и скачивается заново.

---

## Что планируется добавить

- [ ] **Интеграционные тесты** — Spring Boot Test + Testcontainers (реальная PostgreSQL в Docker)
- [ ] **Refresh-токены** — JWT access (15 мин) + refresh (7 дней) с ротацией
- [ ] **Категории как отдельная сущность** — вместо строки в поле `category`
- [ ] **Рейтинги и отзывы** — `Review` сущность, средний рейтинг товара
- [ ] **Поиск по полнотексту** — PostgreSQL `tsvector` или Elasticsearch
- [ ] **Email-уведомления** — Spring Mail при смене статуса заказа
- [ ] **Расширенный PaymentService** — интеграция с платёжным шлюзом
- [ ] **Логирование запросов** — AOP-аспект `@Around` для замера времени выполнения
