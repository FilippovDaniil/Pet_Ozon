# OpenSearch — Полнотекстовый поиск

Документ описывает **все нюансы** подключения OpenSearch к Spring Boot 3.x проекту.  
Написан по опыту интеграции в `Pet_Ozon`: каждая ловушка из раздела «Сводная таблица» была реально встречена.

---

## Мотивация

PostgreSQL `LIKE '%запрос%'` не использует B-tree индексы → полный скан таблицы на каждый запрос.  
OpenSearch решает это через инвертированный индекс + анализаторы текста:

| Возможность | PostgreSQL LIKE | OpenSearch |
|---|---|---|
| Full-text поиск | ❌ медленный LIKE | ✅ анализатор + TF-IDF |
| Поиск по нескольким полям | ❌ писать AND OR вручную | ✅ `multi_match` |
| Нечёткий поиск (опечатки) | ❌ нет | ✅ `fuzziness: AUTO` |
| Релевантность | ❌ нет | ✅ по умолчанию |
| Фильтры + полный текст | ❌ сложно | ✅ `bool.must` |

В этом проекте (~320 товаров) PostgreSQL справился бы, но OpenSearch показывает промышленный подход.

---

## Зависимости (build.gradle)

```groovy
// OpenSearch Java Client — Query DSL, индексирование, поиск
implementation 'org.opensearch.client:opensearch-java:2.15.0'

// Транспорт: Apache HttpClient 5.x
// ⚠️ КРИТИЧЕСКИ ВАЖНО: НЕ указывать версию явно!
// Spring Boot 3.4.4 BOM управляет версией (5.4.x).
// Если явно указать 5.3.x → NoClassDefFoundError: TlsSocketStrategy при старте приложения.
implementation 'org.apache.httpcomponents.client5:httpclient5'
```

### Почему не `RestClientTransport`

Официальные гайды и AI-ассистенты часто показывают устаревший вариант:

```java
// ❌ НЕ РАБОТАЕТ в Spring Boot 3.x:
RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
```

**Причина:** `RestClientTransport` требует `opensearch-rest-client`, который тянет `httpclient` 4.x  
(`org.apache.http`). В Spring Boot 3.x зависимость `httpclient` (4.x) **удалена из BOM**.

Правильный подход — `ApacheHttpClient5TransportBuilder` (httpclient5):

```java
// ✅ ПРАВИЛЬНО для Spring Boot 3.x:
HttpHost httpHost = new HttpHost(scheme, host, port);
OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
        .builder(httpHost)
        .setMapper(new JacksonJsonpMapper())
        .build();
```

---

## Конфигурация — OpenSearchConfig.java

```java
package com.example.marketplace.config;

import org.apache.hc.core5.http.HttpHost;          // ← httpclient5, не org.apache.http!
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        // ⚠️ ВАЖНО: в httpclient5 порядок параметров HttpHost — (scheme, host, port)
        // В httpclient4 было: (host, port, scheme) — легко перепутать, результат: NPE
        HttpHost httpHost = new HttpHost(scheme, host, port);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())  // Jackson для сериализации JSON
                .build();
        return new OpenSearchClient(transport);
    }
}
```

**Ключевые свойства** (`application.properties`):
```properties
# По умолчанию localhost:9200 — для локальной разработки без Docker
opensearch.host=localhost
opensearch.port=9200
opensearch.scheme=http
```

Spring Boot автоматически конвертирует переменные окружения:
```
OPENSEARCH_HOST → opensearch.host
OPENSEARCH_PORT → opensearch.port
```

---

## Модель документа — ProductDocument.java

```java
package com.example.marketplace.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {
    private String id;           // _id в OpenSearch — ВСЕГДА String (даже если в БД Long)
    private String name;
    private String description;
    private Double price;        // Double, не BigDecimal — JSON не поддерживает BigDecimal напрямую
    private String category;     // keyword-поле: анализатор не применяется → точный match через term
    private String shopName;
    private Integer stockQuantity;
}
```

**Это не JPA-сущность.** Обычный POJO — OpenSearch сам создаёт динамический маппинг.

Маппинг, который создаст OpenSearch автоматически:
- `name`, `description` → `text` (анализируется, разбивается на токены)
- `category` → `text` + `keyword` (dual mapping: поиск + точный match)
- `price` → `float`

---

## ProductSearchService — ключевые паттерны

### Принцип graceful degradation

Все публичные методы оборачивают OpenSearch-вызовы в `try-catch`:

```java
public void indexProduct(Product product) {
    try {
        openSearchClient.index(r -> r
                .index(INDEX)
                .id(String.valueOf(product.getId()))
                .document(toDocument(product)));
    } catch (Exception e) {
        // НЕ бросаем исключение — каталог PostgreSQL продолжает работать
        log.warn("Failed to index product id={}: {}", product.getId(), e.getMessage());
    }
}
```

Если OpenSearch недоступен:
- `GET /api/products` — работает (PostgreSQL + JPA)
- `GET /api/search/products` — возвращает пустую страницу, не 500

### @PostConstruct ensureIndex()

```java
@PostConstruct
public void ensureIndex() {
    try {
        boolean exists = openSearchClient.indices().exists(r -> r.index("products")).value();
        if (!exists) {
            openSearchClient.indices().create(r -> r.index("products"));
            log.info("OpenSearch index 'products' created");
        }
    } catch (Exception e) {
        log.warn("OpenSearch unavailable at startup: {}", e.getMessage());
    }
}
```

### Построение поискового запроса

```java
public Page<ProductDocument> search(String query, String category,
                                     BigDecimal minPrice, BigDecimal maxPrice,
                                     Pageable pageable) {
    try {
        List<Query> clauses = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            // multi_match: ищет по двум полям одновременно, объединяет релевантность
            clauses.add(Query.of(q -> q.multiMatch(m -> m
                    .fields(List.of("name", "description"))
                    .query(query))));
        }
        if (category != null && !category.isBlank()) {
            // ⚠️ ЛОВУШКА: .value() принимает FieldValue, не String!
            clauses.add(Query.of(q -> q.term(t -> t
                    .field("category")
                    .value(FieldValue.of(category)))));   // ← обязательно FieldValue.of()
        }
        if (minPrice != null) {
            // ⚠️ ЛОВУШКА: диапазон принимает JsonData, не BigDecimal!
            clauses.add(Query.of(q -> q.range(r -> r
                    .field("price")
                    .gte(JsonData.of(minPrice.doubleValue())))));
        }
        if (maxPrice != null) {
            clauses.add(Query.of(q -> q.range(r -> r
                    .field("price")
                    .lte(JsonData.of(maxPrice.doubleValue())))));
        }

        // Нет фильтров → match_all. Есть → bool.must (AND всех условий)
        Query finalQuery = clauses.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.must(clauses)));

        SearchRequest request = new SearchRequest.Builder()
                .index("products")
                .from((int) pageable.getOffset())   // OFFSET для пагинации
                .size(pageable.getPageSize())         // LIMIT
                .query(finalQuery)
                .build();

        SearchResponse<ProductDocument> response =
                openSearchClient.search(request, ProductDocument.class);

        List<ProductDocument> hits = response.hits().hits().stream()
                .map(h -> h.source())
                .toList();
        long total = response.hits().total() != null
                ? response.hits().total().value()
                : hits.size();

        return new PageImpl<>(hits, pageable, total);
    } catch (Exception e) {
        log.warn("OpenSearch search failed: {}", e.getMessage());
        return Page.empty(pageable);   // graceful degradation
    }
}
```

---

## Критическая ловушка: LazyInitializationException в reindexAll

`AppConfig.initData()` — это `CommandLineRunner`. JPA-сессия создаётся и закрывается внутри  
каждого вызова репозитория. После `productRepository.findAll()` сессия закрыта.  
При обращении к lazy-ассоциациям (`product.getCategory().getName()`) Hibernate пытается  
инициализировать прокси — но сессии нет → `LazyInitializationException`.

```java
// ❌ ОШИБКА: после возврата из findAll() сессия закрыта
productSearchService.reindexAll(productRepository.findAll());
// → "Could not initialize proxy [Category#1] - no Session"
```

**Решение:** JOIN FETCH загружает ассоциации в рамках одного запроса — прокси не нужны:

```java
// В ProductRepository:
@Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.seller")
List<Product> findAllForReindex();

// В AppConfig:
productSearchService.reindexAll(productRepository.findAllForReindex());
```

SQL, который генерирует Hibernate:
```sql
SELECT p.*, c.*, s.*
FROM products p
LEFT JOIN categories c ON c.id = p.category_id
LEFT JOIN users s ON s.id = p.seller_id
```

Категории и продавцы уже в памяти — никаких прокси.

---

## Интеграция в сервисы

При каждом изменении товара через `ProductService` или `SellerService`:

```java
// createProduct():
Product saved = productRepository.save(product);
productSearchService.indexProduct(saved);   // индексируем после сохранения

// updateProduct():
Product saved = productRepository.save(product);
productSearchService.indexProduct(saved);   // переиндексируем

// deleteProduct():
productRepository.deleteById(id);
productSearchService.removeProduct(id);     // удаляем из индекса
```

Синхронизация при старте (AppConfig):
```java
productSearchService.reindexAll(productRepository.findAllForReindex());
```

---

## Docker Compose

```yaml
opensearch:
  image: opensearchproject/opensearch:2.17.0
  environment:
    - discovery.type=single-node          # без кластеризации (dev режим)
    - DISABLE_SECURITY_PLUGIN=true         # HTTP без TLS и Basic Auth — ТОЛЬКО для dev!
    - bootstrap.memory_lock=false          # управляется через limits
    - OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m   # фиксируем heap
  ports:
    - "9200:9200"
  volumes:
    - opensearch_data:/usr/share/opensearch/data
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 40s

app:
  environment:
    OPENSEARCH_HOST: opensearch    # ← имя сервиса в docker-compose сети, не localhost!
    OPENSEARCH_PORT: "9200"
    OPENSEARCH_SCHEME: http
  depends_on:
    opensearch:
      condition: service_healthy   # ждём healthcheck перед стартом приложения
```

---

## Kubernetes — 08-opensearch.yaml

### vm.max_map_count — обязательное требование

OpenSearch падает без этой настройки:
```
max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
```

В Rancher Desktop (k3s в Linux VM) устанавливается через privileged initContainer:

```yaml
initContainers:
  - name: sysctl-fix
    image: busybox:1.36
    command: ["sysctl", "-w", "vm.max_map_count=262144"]
    securityContext:
      privileged: true   # требуется для изменения параметра ядра
```

### imagePullPolicy для OpenSearch

```yaml
containers:
  - name: opensearch
    image: opensearchproject/opensearch:2.17.0
    imagePullPolicy: IfNotPresent   # ← НЕ Never!
```

`imagePullPolicy: Never` — только для кастомных образов (marketplace-app), которые мы загружаем вручную.  
Публичные образы (opensearch, postgres, grafana) используют `IfNotPresent`.

### initContainer wait-for-opensearch в 06-app.yaml

```yaml
initContainers:
  - name: wait-for-opensearch
    image: busybox:1.36
    command:
      - sh
      - -c
      - |
        until nc -z opensearch-service 9200; do
          echo "Waiting for opensearch-service:9200..."
          sleep 3
        done
        echo "OpenSearch is ready!"
```

Без этого: Spring Boot стартует до готовности OpenSearch, `AppConfig.reindexAll()` вызывается  
немедленно, первый документ не индексируется → цикл прерывается → индекс пуст.

### Имена сервисов

| Среда | Переменная | Значение |
|---|---|---|
| Docker Compose | `OPENSEARCH_HOST` | `opensearch` (имя сервиса) |
| Kubernetes | `OPENSEARCH_HOST` | `opensearch-service` (имя K8s Service) |

Не `localhost` — приложение и OpenSearch в разных контейнерах.

### Ресурсы

```yaml
resources:
  requests:
    memory: "600Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

Без limits OpenSearch может занять всю память ноды и вытолкнуть другие поды.  
Фиксация heap (`-Xms512m -Xmx512m`) предотвращает динамическое расширение выше лимита.

---

## Поисковый контроллер

```
GET /api/search/products
```

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `q` | String | нет | Полнотекстовый запрос по `name` и `description` |
| `category` | String | нет | Точная фильтрация по категории |
| `minPrice` | BigDecimal | нет | Цена от |
| `maxPrice` | BigDecimal | нет | Цена до |
| `page` | int | нет | Номер страницы (от 0) |
| `size` | int | нет | Размер страницы (по умолчанию 20) |

Примеры:
```
GET /api/search/products?q=ноутбук
GET /api/search/products?category=Наушники&maxPrice=10000
GET /api/search/products?q=sony&minPrice=5000&maxPrice=30000&page=0&size=10
GET /api/search/products                   # match_all — все товары
```

Доступ: `permitAll()` — без авторизации (как и `GET /api/products`).

---

## Сводная таблица нюансов

| # | Проблема | Симптом | Решение |
|---|----------|---------|---------|
| 1 | `httpclient5:5.3.x` с явной версией | `NoClassDefFoundError: TlsSocketStrategy` | Убрать версию — BOM даёт 5.4.x |
| 2 | `RestClientTransport` (httpclient4) | `ClassNotFoundException: org.apache.http.HttpHost` | Использовать `ApacheHttpClient5TransportBuilder` |
| 3 | Неверный порядок параметров `HttpHost` | NPE или неверный хост | httpclient5: `(scheme, host, port)` — отличается от 4.x |
| 4 | `TermQuery.value(String)` | Ошибка компиляции в opensearch-java 2.x | `.value(FieldValue.of(category))` |
| 5 | `RangeQuery` с `BigDecimal` | Ошибка компиляции/runtime | `JsonData.of(price.doubleValue())` |
| 6 | `findAll()` в `reindexAll` | `LazyInitializationException: Category` | `findAllForReindex()` с `JOIN FETCH` |
| 7 | `vm.max_map_count` в K8s | OpenSearch выходит с ошибкой (Exit 78) | Privileged initContainer `sysctl` |
| 8 | `imagePullPolicy: Never` для OpenSearch | `ErrImageNeverPull` | `IfNotPresent` для публичных образов |
| 9 | Нет `wait-for-opensearch` initContainer | `reindexAll` вызван до готовности — индекс пуст | initContainer в `06-app.yaml` |
| 10 | `OPENSEARCH_HOST=localhost` в Docker/K8s | `Connection refused` — приложение ищет себя | Указать имя сервиса: `opensearch` / `opensearch-service` |

---

## Проверка работоспособности

```powershell
# Прямой доступ к OpenSearch через port-forward (K8s)
kubectl port-forward -n marketplace svc/opensearch-service 9200:9200

# Статус кластера — должен быть green или yellow
curl http://localhost:9200/_cluster/health

# Количество документов в индексе products
curl http://localhost:9200/products/_count
# Ожидаем: {"count":320,...}

# Поиск через port-forward (K8s)
curl "http://localhost:9200/products/_search?q=ноутбук&size=3&pretty"

# Поиск через API приложения
curl "http://localhost:30667/api/search/products?q=ноутбук&size=5"
```

Docker Compose — OpenSearch доступен напрямую:
```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:9200/products/_count
```
