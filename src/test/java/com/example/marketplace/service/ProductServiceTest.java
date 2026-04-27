package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты ProductService: каталог товаров, CRUD, динамические фильтры.
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;

    @InjectMocks ProductService productService;

    // Вспомогательный метод: создаёт продукт для тестов
    private Product makeProduct(Long id, String name, BigDecimal price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStockQuantity(stock);
        return p;
    }

    // ── getAllProducts ────────────────────────────────────────────────────────

    @Test
    void getAllProducts_noFilters_returnsPageOfResponses() {
        PageImpl<Product> products = new PageImpl<>(List.of(
                makeProduct(1L, "Ноутбук", new BigDecimal("79999.99"), 10),
                makeProduct(2L, "Мышь",    new BigDecimal("1999.99"),  50)
        ));
        // Specification — динамический SQL-фильтр. any(Specification.class) принимает любой фильтр.
        // Pageable — параметры пагинации и сортировки. any(Pageable.class) принимает любые.
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(products);

        // Все фильтры null → никаких условий WHERE в SQL
        Page<ProductResponse> result = productService.getAllProducts(null, null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Ноутбук");
        assertThat(result.getContent().get(1).getName()).isEqualTo("Мышь");
    }

    @Test
    void getAllProducts_empty_returnsEmptyPage() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(productService.getAllProducts(null, null, null, null, Pageable.unpaged()).getContent()).isEmpty();
    }

    @Test
    void getAllProducts_withFilters_passesSpecToRepository() {
        // Тест не проверяет саму Specification (это сложно с моком), только то,
        // что репозиторий был вызван — значит фильтр был передан.
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        productService.getAllProducts("Ноутбук", null, new BigDecimal("5000"), new BigDecimal("100000"), Pageable.unpaged());

        // verify подтверждает что findAll был вызван (с фильтрами из параметров)
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── getProductById ────────────────────────────────────────────────────────

    @Test
    void getProductById_found_returnsResponse() {
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(makeProduct(1L, "Ноутбук", new BigDecimal("79999.99"), 10)));

        ProductResponse result = productService.getProductById(1L);

        // Проверяем все ключевые поля DTO-ответа
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Ноутбук");
        assertThat(result.getPrice()).isEqualByComparingTo("79999.99");
        assertThat(result.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void getProductById_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── createProduct ─────────────────────────────────────────────────────────

    @Test
    void createProduct_savesAndReturnsResponse() {
        // CreateProductRequest — DTO с данными нового товара от клиента
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Клавиатура");
        req.setDescription("Механическая");
        req.setPrice(new BigDecimal("3999.00"));
        req.setStockQuantity(20);

        Product saved = makeProduct(3L, "Клавиатура", new BigDecimal("3999.00"), 20);
        saved.setDescription("Механическая");
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductResponse result = productService.createProduct(req);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getName()).isEqualTo("Клавиатура");
        assertThat(result.getStockQuantity()).isEqualTo(20);
        verify(productRepository).save(any(Product.class)); // убеждаемся что save был вызван
    }

    // ── updateProduct ─────────────────────────────────────────────────────────

    @Test
    void updateProduct_found_updatesAllFields() {
        Product existing = makeProduct(1L, "Старое", new BigDecimal("1000.00"), 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        // Запрос с новыми значениями всех полей
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Новое");
        req.setPrice(new BigDecimal("2000.00"));
        req.setStockQuantity(15);

        Product updated = makeProduct(1L, "Новое", new BigDecimal("2000.00"), 15);
        when(productRepository.save(any(Product.class))).thenReturn(updated);

        ProductResponse result = productService.updateProduct(1L, req);

        // Все поля должны обновиться
        assertThat(result.getName()).isEqualTo("Новое");
        assertThat(result.getPrice()).isEqualByComparingTo("2000.00");
        assertThat(result.getStockQuantity()).isEqualTo(15);
    }

    @Test
    void updateProduct_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, new CreateProductRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── deleteProduct ─────────────────────────────────────────────────────────

    @Test
    void deleteProduct_found_deletesById() {
        when(productRepository.existsById(1L)).thenReturn(true); // товар существует

        productService.deleteProduct(1L);

        // Убеждаемся что deleteById был вызван с правильным id
        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_notFound_throwsException() {
        when(productRepository.existsById(99L)).thenReturn(false); // товара нет

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        // never() — убеждаемся что deleteById не вызывался (нечего удалять)
        verify(productRepository, never()).deleteById(any());
    }
}
