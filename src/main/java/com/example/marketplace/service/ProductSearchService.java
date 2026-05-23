package com.example.marketplace.service;

import com.example.marketplace.entity.Product;
import com.example.marketplace.search.ProductDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Сервис полнотекстового поиска по товарам через OpenSearch.
// Принцип graceful degradation: если OpenSearch недоступен, методы логируют предупреждение
// и не бросают исключений — основной каталог (PostgreSQL) продолжает работать.
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final OpenSearchClient openSearchClient;
    private static final String INDEX = "products";

    // @PostConstruct вызывается один раз после инъекции всех зависимостей.
    // Создаёт индекс если не существует. При недоступном OpenSearch — только warning.
    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = openSearchClient.indices().exists(r -> r.index(INDEX)).value();
            if (!exists) {
                openSearchClient.indices().create(r -> r.index(INDEX));
                log.info("OpenSearch index '{}' created", INDEX);
            } else {
                log.info("OpenSearch index '{}' already exists", INDEX);
            }
        } catch (Exception e) {
            log.warn("OpenSearch unavailable at startup, index check skipped: {}", e.getMessage());
        }
    }

    // Индексирует один товар. Используется при создании и обновлении из ProductService/SellerService.
    public void indexProduct(Product product) {
        try {
            openSearchClient.index(r -> r
                    .index(INDEX)
                    .id(String.valueOf(product.getId()))
                    .document(toDocument(product)));
        } catch (Exception e) {
            log.warn("Failed to index product id={}: {}", product.getId(), e.getMessage());
        }
    }

    // Удаляет документ из индекса. Используется при удалении товара.
    public void removeProduct(Long id) {
        try {
            openSearchClient.delete(r -> r.index(INDEX).id(String.valueOf(id)));
        } catch (Exception e) {
            log.warn("Failed to remove product id={} from index: {}", id, e.getMessage());
        }
    }

    // Полная переиндексация: вызывается при старте приложения из AppConfig.
    // Гарантирует что индекс синхронизирован с PostgreSQL.
    public void reindexAll(List<Product> products) {
        log.info("Reindexing {} products to OpenSearch...", products.size());
        int success = 0;
        for (Product p : products) {
            try {
                openSearchClient.index(r -> r
                        .index(INDEX)
                        .id(String.valueOf(p.getId()))
                        .document(toDocument(p)));
                success++;
            } catch (Exception e) {
                log.warn("Reindex failed for product id={}: {}", p.getId(), e.getMessage());
                break; // если первый документ не прошёл — OpenSearch недоступен, прекращаем
            }
        }
        if (success > 0) {
            log.info("Reindex complete: {}/{} products indexed", success, products.size());
        }
    }

    // Полнотекстовый поиск с фильтрами по категории и цене, с пагинацией.
    // При недоступном OpenSearch возвращает пустую страницу.
    public Page<ProductDocument> search(String query, String category,
                                         BigDecimal minPrice, BigDecimal maxPrice,
                                         Pageable pageable) {
        try {
            List<Query> clauses = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                // multi_match — ищет query в полях name и description одновременно
                clauses.add(Query.of(q -> q.multiMatch(m -> m
                        .fields(List.of("name", "description"))
                        .query(query))));
            }
            if (category != null && !category.isBlank()) {
                // term — точное совпадение по keyword-полю category (без анализа)
                clauses.add(Query.of(q -> q.term(t -> t.field("category").value(FieldValue.of(category)))));
            }
            if (minPrice != null) {
                clauses.add(Query.of(q -> q.range(r -> r
                        .field("price")
                        .gte(JsonData.of(minPrice.doubleValue())))));
            }
            if (maxPrice != null) {
                clauses.add(Query.of(q -> q.range(r -> r
                        .field("price")
                        .lte(JsonData.of(maxPrice.doubleValue())))));
            }

            // Если фильтров нет — match_all, иначе bool.must со всеми условиями
            Query finalQuery = clauses.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.must(clauses)));

            SearchRequest request = new SearchRequest.Builder()
                    .index(INDEX)
                    .from((int) pageable.getOffset())
                    .size(pageable.getPageSize())
                    .query(finalQuery)
                    .build();

            SearchResponse<ProductDocument> response = openSearchClient.search(request, ProductDocument.class);

            List<ProductDocument> hits = response.hits().hits().stream()
                    .map(h -> h.source())
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : hits.size();

            return new PageImpl<>(hits, pageable, total);
        } catch (Exception e) {
            log.warn("OpenSearch search failed, returning empty result: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    // Конвертирует JPA-сущность в плоский документ для OpenSearch.
    private ProductDocument toDocument(Product product) {
        return new ProductDocument(
                String.valueOf(product.getId()),
                product.getName(),
                product.getDescription(),
                product.getPrice() != null ? product.getPrice().doubleValue() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getSeller() != null ? product.getSeller().getShopName() : null,
                product.getStockQuantity()
        );
    }
}
