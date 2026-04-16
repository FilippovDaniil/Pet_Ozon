# Pet_Ozon — Marketplace REST API

Учебный pet-проект: REST API маркетплейса на Spring Boot 3.  
Покупатели просматривают товары, добавляют в корзину и оформляют заказы. Продавцы управляют своими товарами и видят заработок. Администратор управляет всем каталогом и заказами.

---

## Содержание

- [Стек](#стек)
- [Архитектура](#архитектура)
- [Быстрый старт](#быстрый-старт)
  - [Запуск через Docker](#запуск-через-docker)
  - [Запуск локально](#запуск-локально)
- [Тестовые данные](#тестовые-данные)
- [API Reference](#api-reference)
  - [Товары](#товары)
  - [Корзина](#корзина)
  - [Заказы](#заказы)
  - [Счета и оплата](#счета-и-оплата)
  - [Продавец](#продавец)
  - [Администратор](#администратор)
- [Бизнес-логика](#бизнес-логика)
- [Тесты](#тесты)
- [Что планируется добавить](#что-планируется-добавить)

---

## Стек

| Компонент        | Версия                   |
|------------------|--------------------------|
| Java             | 21 (Oracle HotSpot)      |
| Gradle           | 9.1.0                    |
| Spring Boot      | 3.4.4                    |
| Spring Data JPA  | (управляется BOM)        |
| Spring Validation| (управляется BOM)        |
| PostgreSQL       | 15+                      |
| Lombok           | (управляется BOM)        |
| JUnit 5 + Mockito| (управляется BOM)        |
| Docker / Compose | любая современная версия |

---

## Архитектура

### Слои приложения

```
Controller → Service → Repository → Entity
```

Контроллеры принимают HTTP-запросы и возвращают DTO.  
Сервисы содержат бизнес-логику и работают с JPA-репозиториями.  
Все ошибки обрабатываются централизованно через `GlobalExceptionHandler` (`@RestControllerAdvice`).

### Связи между сущностями

```
User ──1:1──► Cart ──1:N──► CartItem ──N:1──► Product
User ──1:N──► Order ──1:N──► OrderItem ──N:1──► Product
              Order ──1:1──► Invoice ──1:N──► Payment
Product ──N:1──► User (seller)
```

### Структура пакетов

```
com.example.marketplace
├── config/
│   ├── AppConfig.java          ← CommandLineRunner: создаёт тестовых пользователей и товары
│   └── CorsConfig.java         ← разрешает запросы с фронтенда
├── controller/
│   ├── ProductController       GET  /api/products, /api/products/{id}
│   ├── CartController          GET/POST /api/cart, /add, /remove, /update, /checkout
│   ├── OrderController         GET  /api/orders/my, /api/orders/{id}
│   ├── InvoiceController       GET  /api/invoice/{id}
│   │                           POST /api/invoice/{id}/pay
│   ├── SellerController        GET/POST/PUT/DELETE /api/seller/products
│   │                           GET  /api/seller/balance, /api/seller/sales
│   └── AdminController         POST/PUT/DELETE /api/admin/products
│                               GET/PUT /api/admin/orders, /api/admin/invoices
├── service/                    ← бизнес-логика
├── repository/                 ← 8 JpaRepository-интерфейсов
├── entity/
│   ├── User, Product, Cart, CartItem
│   ├── Order, OrderItem, Invoice, Payment
│   └── enums/
│       ├── Role            CLIENT | SELLER | ADMIN
│       ├── OrderStatus     CREATED | PAID | DELIVERED | CANCELLED
│       ├── InvoiceStatus   UNPAID | PAID
│       └── PaymentStatus   SUCCESS | FAILED
├── dto/
│   ├── request/            AddToCartRequest, CheckoutRequest, CreateProductRequest,
│   │                       UpdateCartItemRequest, PaymentRequest, UpdateOrderStatusRequest
│   └── response/           CartResponse, CartItemResponse, OrderResponse, OrderItemResponse,
│                           ProductResponse, InvoiceResponse, PaymentResponse,
│                           SellerResponse, ErrorResponse
└── exception/
    ├── ResourceNotFoundException   → HTTP 404
    └── GlobalExceptionHandler      → возвращает ErrorResponse
```

### Аутентификация (заглушка)

Spring Security не подключён. Текущий пользователь определяется по заголовку `X-User-Id`.  
Если заголовок не передан, используется дефолтный id: **1** для покупателя, **3** для продавца.

```http
X-User-Id: 1
```

---

## Быстрый старт

### Запуск через Docker

Самый простой способ — поднять всё одной командой. Docker соберёт JAR внутри контейнера и запустит PostgreSQL автоматически.

```bash
docker compose up --build
```

После запуска API доступен на `http://localhost:8888`.

**Остановка:**
```bash
docker compose down
```

**Только база данных** (для запуска приложения локально из IDE):
```bash
docker compose up postgres
```

### Запуск локально

**Требования:** Java 21, PostgreSQL 15+, Gradle 9+ (или используйте `./gradlew`).

**1. Создайте базу данных:**
```sql
CREATE DATABASE marketplace;
```

**2. Настройте подключение** в `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/marketplace
spring.datasource.username=postgres
spring.datasource.password=1234
```

**3. Соберите и запустите:**
```bash
./gradlew bootRun
```

Приложение само создаёт таблицы (`ddl-auto=update`) и наполняет базу тестовыми данными при первом запуске.

---

## Тестовые данные

При старте `AppConfig` автоматически создаёт 4 пользователей и 20 товаров (если их ещё нет в БД).

### Пользователи

| id | Email                  | Роль   | Магазин    |
|----|------------------------|--------|------------|
| 1  | client@example.com     | CLIENT | —          |
| 2  | admin@example.com      | ADMIN  | —          |
| 3  | seller1@example.com    | SELLER | TechShop   |
| 4  | seller2@example.com    | SELLER | AudioWorld |

Пароль у всех: `pass`

### Товары

**TechShop** (seller_id=3) — 10 товаров: ноутбуки, мониторы, периферия.  
**AudioWorld** (seller_id=4) — 10 товаров: наушники, колонки, микрофоны.

---

## API Reference

Базовый URL: `http://localhost:8888`

Формат ошибок (все статусы 4xx/5xx):
```json
{
  "status": 404,
  "message": "Product not found with id: 99",
  "timestamp": "2025-04-17T12:00:00"
}
```

---

### Товары

#### Получить список всех товаров
```
GET /api/products
```

**Ответ 200:**
```json
[
  {
    "id": 1,
    "name": "Ноутбук Dell XPS 15",
    "description": "Intel Core i7, 16 ГБ RAM...",
    "price": 89999.99,
    "stockQuantity": 10
  }
]
```

#### Получить товар по ID
```
GET /api/products/{id}
```

**Ответ 200** — объект товара.  
**Ответ 404** — товар не найден.

---

### Корзина

Все методы корзины принимают заголовок `X-User-Id`. Без него используется покупатель с id=1.

#### Посмотреть корзину
```
GET /api/cart
X-User-Id: 1
```

**Ответ 200:**
```json
{
  "userId": 1,
  "items": [
    {
      "cartItemId": 3,
      "productId": 1,
      "productName": "Ноутбук Dell XPS 15",
      "price": 89999.99,
      "quantity": 2,
      "subtotal": 179999.98
    }
  ],
  "total": 179999.98
}
```

#### Добавить товар в корзину
```
POST /api/cart/add
X-User-Id: 1
Content-Type: application/json

{
  "productId": 1,
  "quantity": 2
}
```

Если товар уже в корзине — количество суммируется.

**Ответ 200** — обновлённая корзина.  
**Ответ 404** — товар или пользователь не найден.

#### Изменить количество позиции
```
PUT /api/cart/update/{cartItemId}
Content-Type: application/json

{ "quantity": 3 }
```

**Ответ 200** — обновлённая корзина.  
**Ответ 400** — количество ≤ 0.

#### Удалить позицию из корзины
```
DELETE /api/cart/remove/{cartItemId}
```

**Ответ 200.**

#### Оформить заказ (checkout)
```
POST /api/cart/checkout
X-User-Id: 1
Content-Type: application/json

{ "shippingAddress": "Москва, ул. Примерная, 1" }
```

Атомарно создаёт `Order`, `OrderItem`-ы (со снимком цены) и `Invoice`. Корзина очищается.

**Ответ 200** — созданный заказ со всеми позициями.  
**Ответ 400** — корзина пуста.

---

### Заказы

#### Мои заказы
```
GET /api/orders/my
X-User-Id: 1
```

**Ответ 200:**
```json
[
  {
    "id": 1,
    "orderDate": "2025-04-17T10:30:00",
    "status": "CREATED",
    "totalAmount": 179999.98,
    "shippingAddress": "Москва, ул. Примерная, 1",
    "items": [...]
  }
]
```

Статусы заказа: `CREATED` → `PAID` → `DELIVERED` / `CANCELLED`

#### Заказ по ID
```
GET /api/orders/{id}
```

**Ответ 200** — заказ.  
**Ответ 404** — заказ не найден.

---

### Счета и оплата

#### Получить счёт
```
GET /api/invoice/{id}
```

**Ответ 200:**
```json
{
  "id": 1,
  "orderId": 1,
  "amount": 179999.98,
  "status": "UNPAID",
  "createdAt": "2025-04-17T10:30:00",
  "paidAt": null
}
```

#### Оплатить счёт
```
POST /api/invoice/{id}/pay
Content-Type: application/json

{ "paymentMethod": "CARD" }
```

Если `paymentMethod` не передан или пустой — используется `"CARD"`.  
При оплате: счёт переходит в `PAID`, заказ в `PAID`, на балансы продавцов начисляется выручка, создаётся запись `Payment`.

**Ответ 200:**
```json
{
  "id": 1,
  "invoiceId": 1,
  "amount": 179999.98,
  "paymentMethod": "CARD",
  "status": "SUCCESS",
  "timestamp": "2025-04-17T10:35:00"
}
```

**Ответ 400** — счёт уже оплачен.  
**Ответ 404** — счёт не найден.

---

### Продавец

Все методы принимают заголовок `X-User-Id` (id продавца). Без него — id=3 (seller1).  
Если пользователь не найден — `404`. Если пользователь не является продавцом — `400`.

#### Мои товары
```
GET /api/seller/products
X-User-Id: 3
```

**Ответ 200** — список товаров этого продавца.

#### Создать товар
```
POST /api/seller/products
X-User-Id: 3
Content-Type: application/json

{
  "name": "Новый гаджет",
  "description": "Описание",
  "price": 4999.99,
  "stockQuantity": 20,
  "imageUrl": "https://example.com/image.jpg"
}
```

Товар автоматически привязывается к продавцу из заголовка.

**Ответ 201** — созданный товар.

#### Обновить товар
```
PUT /api/seller/products/{id}
X-User-Id: 3
Content-Type: application/json

{
  "name": "Обновлённое название",
  "price": 5499.99,
  "stockQuantity": 15
}
```

**Ответ 200** — обновлённый товар.  
**Ответ 400** — товар не принадлежит этому продавцу.  
**Ответ 404** — товар не найден.

#### Удалить товар
```
DELETE /api/seller/products/{id}
X-User-Id: 3
```

**Ответ 204.**  
**Ответ 400** — товар не принадлежит этому продавцу.

#### Баланс продавца
```
GET /api/seller/balance
X-User-Id: 3
```

**Ответ 200:**
```json
{
  "id": 3,
  "email": "seller1@example.com",
  "fullName": "Алексей Технов",
  "shopName": "TechShop",
  "balance": 15000.00
}
```

#### Продажи (заказы с товарами продавца)
```
GET /api/seller/sales
X-User-Id: 3
```

**Ответ 200** — список заказов, в которых есть хотя бы один товар этого продавца.

---

### Администратор

Без аутентификации (заглушка, доступно всем).

#### Создать товар
```
POST /api/admin/products
Content-Type: application/json

{
  "name": "Планшет",
  "description": "10-дюймовый планшет",
  "price": 29999.99,
  "stockQuantity": 10
}
```

**Ответ 201.**

#### Обновить товар
```
PUT /api/admin/products/{id}
Content-Type: application/json

{ "name": "Обновлённый", "price": 32000.00, "stockQuantity": 8 }
```

**Ответ 200 / 404.**

#### Удалить товар
```
DELETE /api/admin/products/{id}
```

**Ответ 204 / 404.**

#### Все заказы
```
GET /api/admin/orders
```

**Ответ 200** — список всех заказов в системе.

#### Изменить статус заказа
```
PUT /api/admin/orders/{id}/status
Content-Type: application/json

{ "status": "DELIVERED" }
```

Доступные статусы: `CREATED`, `PAID`, `DELIVERED`, `CANCELLED`.

**Ответ 200 / 404.**

#### Все счета
```
GET /api/admin/invoices
```

**Ответ 200** — список всех счетов.

---

## Бизнес-логика

### Оформление заказа

```
POST /api/cart/checkout
        │
        ▼
CartService.checkout()
  1. Проверяет, что корзина не пуста
  2. Создаёт Order (статус CREATED)
  3. Для каждого CartItem создаёт OrderItem
     └─ priceAtOrder = текущая цена товара (снимок)
  4. Считает totalAmount = Σ(priceAtOrder × quantity)
  5. Создаёт Invoice (статус UNPAID, amount = totalAmount)
  6. Очищает корзину
  @Transactional — всё или ничего
```

### Оплата счёта

```
POST /api/invoice/{id}/pay
        │
        ▼
InvoiceService.pay()
  1. Проверяет, что счёт не оплачен
  2. Invoice.status → PAID, Invoice.paidAt = now()
  3. Order.status → PAID
  4. Для каждого OrderItem с продавцом:
     └─ seller.balance += priceAtOrder × quantity
  5. Создаёт Payment (status SUCCESS)
  @Transactional — всё или ничего
```

### Изоляция товаров продавца

Продавец через `/api/seller/products/{id}` может изменить или удалить только свой товар. Попытка обратиться к чужому товару возвращает `400 Bad Request`.

---

## Тесты

Проект покрыт юнит-тестами на уровне сервисов и контроллеров.

```
src/test/java/com/example/marketplace/
├── service/
│   ├── CartServiceTest       — корзина и checkout
│   ├── InvoiceServiceTest    — оплата, начисление баланса продавцу
│   ├── OrderServiceTest      — управление заказами
│   ├── ProductServiceTest    — CRUD товаров
│   ├── SellerServiceTest     — операции продавца (18 тестов)
│   └── UserServiceTest       — поиск пользователей
└── controller/
    ├── AdminControllerTest   — admin-эндпоинты
    ├── CartControllerTest    — корзина
    ├── InvoiceControllerTest — счета
    ├── OrderControllerTest   — заказы
    ├── ProductControllerTest — каталог
    └── SellerControllerTest  — seller-эндпоинты (16 тестов)
```

**Сервисные тесты** — `@ExtendWith(MockitoExtension.class)`, все зависимости мокируются.  
**Контроллерные тесты** — `@WebMvcTest` + `MockMvc`, проверяются HTTP-статусы и JSON-ответы.

**Запуск всех тестов:**
```bash
./gradlew test
```

**HTML-отчёт** после прогона:
```
build/reports/tests/test/index.html
```

---

## Что планируется добавить

- [ ] **Spring Security + JWT** — замена заглушки `X-User-Id` на полноценную аутентификацию
- [ ] **`@Valid` + валидация DTO** — аннотации `@NotBlank`, `@Min`, `@Positive` на входящих запросах
- [ ] **Уменьшение `stockQuantity`** при оформлении заказа
- [ ] **Пагинация** в списках товаров и заказов (`Pageable`)
- [ ] **Интеграционные тесты** — Spring Boot Test + Testcontainers для PostgreSQL
- [ ] **Поиск и фильтрация товаров** по названию, цене, продавцу
- [ ] **Расширенный `PaymentService`** — интеграция с платёжным шлюзом вместо заглушки
