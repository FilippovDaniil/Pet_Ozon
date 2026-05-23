# Pet_Ozon — Marketplace Demo

## Что это

Учебный pet-проект: REST API маркетплейса на Spring Boot 3.x с полноценным фронтендом.
Цель — освоить Spring Boot, JPA, REST, Spring Security, Spring Mail. Автор — начинающий Java-разработчик.

## Стек

| Компонент | Версия / Примечание |
|-----------|---------------------|
| Java | 21 (Oracle HotSpot) |
| Gradle | 9.1.0 |
| Spring Boot | 3.4.4 |
| Spring Data JPA | (управляется BOM) |
| Spring Security + JWT | JWT-фильтр, JwtUtil, SecurityConfig |
| Spring Mail | Яндекс SMTP, порт 465 (SMTPS) |
| PostgreSQL | 15+ (localhost:5432) |
| Lombok | (управляется BOM) |
| Spring Validation | Bean Validation 3.x |
| Spring AOP | LoggingAspect (@Around) |
| Spring Cache | CacheConfig, @Cacheable / @CacheEvict |
| SpringDoc / Swagger UI | http://localhost:8080/swagger-ui.html |
| Testcontainers | интеграционный тест ProductRepository |
| Frontend | Alpine.js 3.14.1 + Tailwind CSS (CDN) |
| SheetJS | xlsx.js 0.18.5 — Excel-выгрузка у бухгалтера |
| Docker Compose | app + PostgreSQL + OpenSearch, файл docker-compose.yml |
| OpenSearch | 2.17.0 (Docker) — движок полнотекстового поиска |
| opensearch-java | 2.15.0 — Java-клиент (ApacheHttpClient5Transport) |
| httpclient5 | BOM-managed (5.4.x) — транспорт OpenSearch; версию НЕ пинить! |

## Подключение к БД

```
url:      jdbc:postgresql://localhost:5432/marketplace
username: postgres
password: 1234
```

База создаётся вручную: `CREATE DATABASE marketplace;`
Таблицы создаются автоматически через `ddl-auto=update`.

## Аутентификация — JWT (реализована)

Используется Bearer-токен в заголовке `Authorization`.
Spring Security настроен в `SecurityConfig`; токен проверяется `JwtAuthenticationFilter`.
Регистрация: `POST /api/auth/register`, вход: `POST /api/auth/login`.

```http
Authorization: Bearer <jwt-token>
```

## Email — Яндекс SMTP (ВАЖНО: безопасность учётных данных)

Пароль приложения Яндекс хранится **только** в `.env` (добавлен в `.gitignore`).
В `application.properties` используется `${MAIL_PASSWORD:}` (env var с пустым fallback).
В `docker-compose.yml` передаётся `MAIL_PASSWORD: "${MAIL_PASSWORD}"`.

```
Отправитель: vorobyshek2015@yandex.ru
SMTP host:   smtp.yandex.ru
Port:        465 (SMTPS / SSL)
```

Пример файла `.env.example` (в гит): `MAIL_PASSWORD=your_yandex_app_password_here`
Никогда не коммитить реальный пароль!

## Структура пакетов

```
com.example.marketplace
├── MarketplaceApplication.java
├── config/
│   ├── AppConfig.java               ← CommandLineRunner: тест-данные + fixRoleConstraint() + reindexAll
│   ├── CacheConfig.java             ← Spring Cache (Caffeine / ConcurrentMap)
│   ├── OpenSearchConfig.java        ← Bean OpenSearchClient (ApacheHttpClient5TransportBuilder)
│   └── SecurityConfig.java          ← HTTP Security + JWT + CORS; GET /api/search/** → permitAll
├── aspect/
│   └── LoggingAspect.java           ← AOP @Around: логирует все вызовы сервисов
├── controller/
│   ├── AuthController               POST /api/auth/register, /login
│   ├── ProductController            GET /api/products (+ фильтры, пагинация)
│   ├── CartController               /api/cart (add, remove, update, checkout)
│   ├── OrderController              /api/orders/my, /{id}
│   ├── InvoiceController            /api/invoice/{id}, /{id}/pay
│   ├── ReviewController             /api/products/{id}/reviews
│   ├── ProfileController            /api/profile
│   ├── AdminController              /api/admin/** (продукты, заказы, счета)
│   ├── EmailController              POST /api/admin/email/send
│   ├── SellerController             /api/seller/** (свои товары, продажи, баланс)
│   ├── ChatController               /api/chat/** (переписка клиент↔продавец, поддержка)
│   ├── AccountantController         /api/accountant/** (отчёты: summary, orders, carts, customers, emails)
│   └── ProductSearchController      GET /api/search/products (OpenSearch, permitAll)
├── service/
│   ├── UserService, ProductService
│   ├── CartService                  ← checkout: Order + Invoice + уменьшение stock
│   ├── OrderService, InvoiceService ← pay(): Payment + отправка чека на email
│   ├── SellerService                ← товары продавца (Page<ProductResponse>), продажи, баланс
│   ├── ReviewService                ← CRUD отзывов, пересчёт среднего рейтинга
│   ├── EmailService                 ← sendOrderReceipt (не бросает), sendCustomEmail (бросает), saveLog
│   ├── ChatService                  ← создание/получение переписок и сообщений
│   ├── AccountantService            ← @PreAuthorize("hasRole('ACCOUNTANT')"), 5 методов отчётов
│   ├── ProductSearchService         ← OpenSearch: ensureIndex, indexProduct, removeProduct, reindexAll, search
│   └── PaymentService               ← заглушка
├── repository/                      ← JpaRepository-интерфейсы для каждой сущности
│                                       ProductRepository: findAllForReindex() с JOIN FETCH (нужен для reindexAll,
│                                       иначе LazyInitializationException при обращении к category/seller)
├── search/
│   └── ProductDocument.java         ← плоский POJO для OpenSearch (не JPA entity; id — String)
├── entity/
│   ├── User, Product, Cart, CartItem
│   ├── Order, OrderItem, Invoice, Payment
│   ├── Review                       ← рейтинг + текст + связь User ↔ Product
│   ├── EmailLog                     ← история отправок (recipient, subject, sentAt, success, errorMessage)
│   ├── Conversation, Message        ← чат
│   └── enums/ Role (CLIENT|SELLER|ADMIN|ACCOUNTANT), OrderStatus, InvoiceStatus, PaymentStatus
├── dto/
│   ├── request/  AddToCartRequest, CheckoutRequest, CreateProductRequest,
│   │             UpdateCartItemRequest, PaymentRequest, UpdateOrderStatusRequest,
│   │             AdminEmailRequest, ReviewRequest, ...
│   └── response/ CartResponse, OrderResponse, ProductResponse, InvoiceResponse,
│                 PaymentResponse, ErrorResponse, AccountantSummaryResponse,
│                 OrderReportDto, CartReportDto, CustomerReportDto, EmailLogDto, ...
└── exception/
    ├── ResourceNotFoundException
    └── GlobalExceptionHandler        ← @RestControllerAdvice: ErrorResponse, 400/403/404/500
```

## Связи между сущностями

```
User ──1:1──► Cart ──1:N──► CartItem ──N:1──► Product
User ──1:N──► Order ──1:N──► OrderItem ──N:1──► Product
             Order ──1:1──► Invoice ──1:N──► Payment
User ──1:N──► Review ──N:1──► Product
User ──1:N──► Conversation ──1:N──► Message
             (Conversation: buyer ↔ seller; поддержка: buyer ↔ ADMIN)
EmailLog     (отдельная таблица email_logs, не связана FK с User)
```

## Тестовые пользователи (создаются автоматически при старте)

| Email | Пароль | Роль | Примечание |
|---|---|---|---|
| `client@example.com` | `pass` | CLIENT | Иван Клиентов |
| `admin@example.com` | `pass` | ADMIN | Администратор |
| `seller1@example.com` | `pass` | SELLER | TechShop (электроника) |
| `seller2@example.com` | `pass` | SELLER | AudioWorld (аудиотехника) |
| `accountant@example.com` | `pass` | ACCOUNTANT | Елена Бухгалтер |

**Товары:** ~250 штук по 12 категориям (создаются при `productRepository.count() < 250`).

## Правила безопасности (SecurityConfig)

```
/api/auth/**          → permitAll (регистрация, вход)
GET /api/products/**  → permitAll (каталог публичный)
/api/admin/**         → hasRole('ADMIN')
/api/seller/**        → hasRole('SELLER')
/api/accountant/**    → hasRole('ACCOUNTANT')
всё остальное         → authenticated
```

Дополнительный уровень: `@PreAuthorize` на методах AccountantService и некоторых других сервисах.

## Фронтенд (Alpine.js + Tailwind CSS)

Статические файлы в `src/main/resources/static/`:

| Файл | Описание |
|------|----------|
| `login.html` | Страница входа (уже мобильная) |
| `register.html` | Регистрация |
| `client.html` | Покупатель: каталог, корзина, заказы, чат |
| `admin.html` | Администратор: товары, заказы, счета, поддержка, почта |
| `seller.html` | Продавец: товары, продажи, баланс, чат |
| `accountant.html` | Бухгалтер: дашборд, отчёты, Excel-выгрузка |
| `profile.html` | Профиль пользователя |
| `js/api.js` | Все HTTP-вызовы к REST API |
| `js/auth.js` | requireAuth(), roleHome(), login(), logout() |

**Мобильная адаптация:** все страницы имеют `meta viewport`, на мобильных (< md = 768px) десктопные
табы скрыты (`hidden md:flex`), вместо них — фиксированная нижняя навигация (`md:hidden fixed bottom-0`).

Роутинг после логина:
```javascript
function roleHome(role) {
    if (role === 'ADMIN')      return 'admin.html';
    if (role === 'SELLER')     return 'seller.html';
    if (role === 'ACCOUNTANT') return 'accountant.html';
    return 'client.html';
}
```

## Критические технические детали

### fixRoleConstraint() в AppConfig
`ddl-auto=update` **не обновляет** существующие CHECK constraints — только создаёт при первом `CREATE TABLE`.
При добавлении нового значения в enum `Role` нужно обновлять constraint вручную.
Решение: `AppConfig.fixRoleConstraint()` — дропает и пересоздаёт `users_role_check` через JdbcTemplate
при каждом старте. Идемпотентен (IF EXISTS).

### Контракт EmailService
- `sendOrderReceipt()` → вызывает `sendHtml()` → при ошибке SMTP **не бросает** исключение.
  Сбой почты не должен откатывать транзакцию оплаты.
- `sendCustomEmail()` → при ошибке SMTP **бросает** RuntimeException.
  Администратор должен видеть ошибку (GlobalExceptionHandler → 500).
- В обоих случаях вызывается `saveLog()` — запись в `email_logs`.

### @Value в тестах
Поле `@Value("${spring.mail.username}") private String from` не инжектируется Mockito.
Решение: `ReflectionTestUtils.setField(emailService, "from", "noreply@marketplace.ru")` в `@BeforeEach`.

## Покрытие тестами

| Тест-класс | Тип | Кол-во тестов |
|---|---|---|
| `EmailServiceTest` | Unit (MockitoExtension) | 8 |
| `EmailControllerTest` | @WebMvcTest | 7 |
| `AccountantServiceTest` | Unit (MockitoExtension) | 12 |
| `AccountantControllerTest` | @WebMvcTest | 10 |
| `InvoiceControllerTest` | @WebMvcTest | 10 |
| `ProductRepositoryIntegrationTest` | Testcontainers | 3+ |

Все `@WebMvcTest` используют `TestSecurityConfig` (@TestConfiguration) вместо реального `SecurityConfig`.
JWT-фильтр исключён через `excludeFilters`. Аутентификация подставляется через `.with(user(...))`.

## Решённые проблемы

1. **Gradle 9.x + Spring Boot 3.2.x** — несовместимы (удалён API `LenientConfiguration.getArtifacts`).
   Решение: обновить плагин до `3.4.4` и `dependency-management` до `1.1.7`.

2. **JAVA_HOME с кавычками** — Windows-переменная содержала кавычки в значении.
   Решение: убрать кавычки через sysdm.cpl → Переменные среды.

3. **users_role_check constraint** — при добавлении ACCOUNTANT в enum `Role` старый constraint
   (без ACCOUNTANT) блокировал INSERT нового пользователя.
   Решение: `fixRoleConstraint()` в AppConfig через JdbcTemplate (DROP + ADD CONSTRAINT).

4. **Бесконечный редирект ACCOUNTANT** — `roleHome()` не имел case для ACCOUNTANT,
   возвращал `client.html`, а там `requireAuth('CLIENT')` видел неверную роль и редиректил снова.
   Решение: добавлен case в `roleHome()` в `auth.js`.

5. **LazyInitializationException** при отправке чека — `order.getUser()` вызывался вне транзакции.
   Решение: доступ к user происходит внутри `@Transactional` в `InvoiceService.pay()`.

6. **NoClassDefFoundError: TlsSocketStrategy** (OpenSearch) — явная версия `httpclient5:5.3.x`
   конфликтует с `HttpClientAutoConfiguration` из Spring Boot 3.4.4, которому нужен 5.4.x.
   Решение: убрать явную версию из build.gradle — BOM управляет ею и даёт 5.4.2.

7. **LazyInitializationException в reindexAll** — `productRepository.findAll()` закрывает JPA-сессию
   до вызова `reindexAll`. Обращение к `product.getCategory().getName()` в `toDocument()` падает,
   потому что Category — lazy proxy без активной сессии.
   Решение: `ProductRepository.findAllForReindex()` с `@Query("... LEFT JOIN FETCH p.category LEFT JOIN FETCH p.seller")` — ассоциации загружаются в памяти до закрытия сессии.

## Что реализовано

- [x] Spring Security + JWT (SecurityConfig, JwtUtil, JwtAuthenticationFilter)
- [x] Роли: CLIENT, SELLER, ADMIN, ACCOUNTANT
- [x] `@Valid` + валидация DTO (`@NotBlank`, `@Email`, `@Min`, `@Positive`)
- [x] `@PreAuthorize` на методах сервисов (двойная защита)
- [x] GlobalExceptionHandler: 400 / 403 / 404 / 500 с ErrorResponse
- [x] Уменьшение `stockQuantity` при оформлении заказа
- [x] Пагинация (`Pageable`, `Page<T>`) для товаров, заказов, счетов
- [x] Логирование: `@Slf4j` + `LoggingAspect` (@Around на все сервисы)
- [x] Spring Cache (`@Cacheable` / `@CacheEvict` в ProductService)
- [x] Рейтинги и отзывы (Review, ReviewService, ReviewController)
- [x] Профиль пользователя (ProfileController)
- [x] Роль SELLER: SellerController, SellerService (Page<ProductResponse>)
- [x] Роль ACCOUNTANT: AccountantController, AccountantService (5 отчётов)
- [x] Чат клиент ↔ продавец + чат с поддержкой (AdminController)
- [x] Email-уведомления: чек после оплаты + произвольное письмо от Admin
- [x] EmailLog: история отправок в таблице email_logs
- [x] Юнит-тесты (Mockito) + @WebMvcTest для всех контроллеров
- [x] Интеграционный тест Testcontainers (ProductRepositoryIntegrationTest)
- [x] Swagger UI / OpenAPI 3 (SpringDoc)
- [x] Docker Compose (app + PostgreSQL)
- [x] **OpenSearch**: полнотекстовый поиск по товарам; `ProductSearchService` (graceful degradation), `ProductSearchController` (`GET /api/search/products`), `ProductDocument` POJO, `OpenSearchConfig` (ApacheHttpClient5Transport); индексация при CRUD товаров, полная переиндексация при старте; подробности — `OpenSearch.md`
- [x] Kubernetes (Rancher Desktop): rancher/k8s/ — 9 манифестов (namespace, secrets, postgres, loki, prometheus, grafana, app, dashboard, opensearch)
- [x] Скрипт деплоя: rancher/deploy.ps1 — один скрипт вместо 4 команд; флаги --build-only, --deploy-only, --reset, --token
- [x] Kubernetes Dashboard — веб-интерфейс управления подами (https://localhost:30443, токен через admin-user-token Secret)
- [x] Helm chart: rancher/helm/marketplace/ — шаблонизированный деплой
- [x] Фронтенд: Alpine.js + Tailwind CSS, 7 статических страниц
- [x] Мобильный фронтенд: нижняя навигация для client/admin/seller/accountant
- [x] Excel-выгрузка в бухгалтерии (SheetJS / xlsx.js)
- [x] Загрузка фото товаров (продавец + админ)
- [x] Расширенный каталог: ~250 товаров по 12 категориям
- [x] Построчные комментарии на русском во всех новых файлах (учебный формат)
