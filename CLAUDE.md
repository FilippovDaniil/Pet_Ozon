# Pet_Ozon — Marketplace Demo

## Что это

Учебный pet-проект: REST API маркетплейса на Spring Boot 3.x.
Цель — освоить Spring Boot, JPA, REST. Автор — начинающий Java-разработчик.

## Стек

| Компонент | Версия |
|-----------|--------|
| Java | 21 (Oracle HotSpot) |
| Gradle | 9.1.0 |
| Spring Boot | 3.4.4 |
| Spring Data JPA | (управляется BOM) |
| PostgreSQL | 15+ (localhost:5432) |
| Lombok | (управляется BOM) |
| Spring Validation | (управляется BOM, для будущих валидаций) |

## Подключение к БД

```
url:      jdbc:postgresql://localhost:5432/marketplace
username: postgres
password: 1234
```

База создаётся вручную: `CREATE DATABASE marketplace;`
Таблицы создаются автоматически через `ddl-auto=update`.

## Структура пакетов

```
com.example.marketplace
├── MarketplaceApplication.java          ← точка входа
├── config/AppConfig.java                ← CommandLineRunner (тестовые данные)
├── controller/
│   ├── ProductController                GET /api/products, /api/products/{id}
│   ├── CartController                   GET /api/cart, POST /add /remove /update /checkout
│   ├── OrderController                  GET /api/orders/my, /api/orders/{id}
│   ├── InvoiceController                GET /api/invoice/{id}, POST /{id}/pay
│   └── AdminController                  /api/admin/products, /orders, /invoices
├── service/
│   ├── UserService, ProductService
│   ├── CartService                      ← checkout создаёт Order + Invoice
│   ├── OrderService, InvoiceService     ← pay() меняет статус, создаёт Payment
│   └── PaymentService                   ← заглушка, расширить позже
├── repository/                          ← 8 JpaRepository-интерфейсов
├── entity/
│   ├── User, Product, Cart, CartItem
│   ├── Order, OrderItem, Invoice, Payment
│   └── enums/ Role, OrderStatus, InvoiceStatus, PaymentStatus
├── dto/
│   ├── request/  AddToCartRequest, CheckoutRequest, CreateProductRequest,
│   │             UpdateCartItemRequest, PaymentRequest, UpdateOrderStatusRequest
│   └── response/ CartResponse, CartItemResponse, OrderResponse, OrderItemResponse,
│                 ProductResponse, InvoiceResponse, PaymentResponse, ErrorResponse
└── exception/
    ├── ResourceNotFoundException
    └── GlobalExceptionHandler           ← @RestControllerAdvice, возвращает ErrorResponse
```

## Связи между сущностями

```
User ──1:1──► Cart ──1:N──► CartItem ──N:1──► Product
User ──1:N──► Order ──1:N──► OrderItem ──N:1──► Product
             Order ──1:1──► Invoice ──1:N──► Payment
```

## Аутентификация (временная заглушка)

Spring Security **не подключён** — добавить позже.
Текущий пользователь определяется по заголовку `X-User-Id`.
Если заголовок не передан — используется userId=1 (первый пользователь из БД).

```http
X-User-Id: 1
```

## Тестовые данные (создаются автоматически при старте)

**Пользователи:**
- `client@example.com` / `pass` — роль CLIENT, id=1
- `admin@example.com` / `pass` — роль ADMIN, id=2

**Товары (5 штук):** ноутбук, мышь, клавиатура, монитор, наушники

## Быстрая проверка API

```bash
# Список товаров
GET http://localhost:8080/api/products

# Добавить в корзину
POST http://localhost:8080/api/cart/add
X-User-Id: 1
Content-Type: application/json
{"productId": 1, "quantity": 2}

# Посмотреть корзину
GET http://localhost:8080/api/cart
X-User-Id: 1

# Оформить заказ
POST http://localhost:8080/api/cart/checkout
X-User-Id: 1
Content-Type: application/json
{"shippingAddress": "Москва, ул. Примерная, 1"}

# Оплатить счёт
POST http://localhost:8080/api/invoice/1/pay
Content-Type: application/json
{"paymentMethod": "CARD"}

# Создать товар (админ)
POST http://localhost:8080/api/admin/products
Content-Type: application/json
{"name": "Планшет", "price": 29999.99, "stockQuantity": 10}
```

## Решённые проблемы

1. **Gradle 9.x + Spring Boot 3.2.x** — несовместимы (удалён API `LenientConfiguration.getArtifacts`).
   Решение: обновить плагин до `3.4.4` и `dependency-management` до `1.1.7`.

2. **JAVA_HOME с кавычками** — Windows-переменная `JAVA_HOME` содержала кавычки в значении
   (`"C:\Program Files\Java\jdk-21.0.10"` вместо `C:\Program Files\Java\jdk-21.0.10`).
   Решение: убрать кавычки через sysdm.cpl → Переменные среды.

## Что добавить следующим (приоритет)

- [ ] `@Valid` + валидация входных DTO (аннотации `@NotBlank`, `@Min`, `@Positive`)
- [ ] Spring Security + JWT-аутентификация
- [ ] Уменьшение `stockQuantity` при оформлении заказа
- [ ] Пагинация в списках товаров и заказов (`Pageable`)
- [ ] Логирование (SLF4J уже подключён через Lombok `@Slf4j`)
- [ ] Тесты (Spring Boot Test + Testcontainers для PostgreSQL)
