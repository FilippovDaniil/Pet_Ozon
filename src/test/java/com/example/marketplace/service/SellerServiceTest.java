package com.example.marketplace.service;

import com.example.marketplace.dto.request.CreateProductRequest;
import com.example.marketplace.dto.response.ProductResponse;
import com.example.marketplace.dto.response.SellerResponse;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты SellerService: управление товарами продавца, баланс, заказы.
// SellerService содержит логику проверки владения (продавец может редактировать только свои товары).
@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock ProductRepository productRepository;
    @Mock UserRepository    userRepository;
    @Mock OrderRepository   orderRepository;
    // ProductService тоже мокируем: SellerService делегирует ему преобразование Product → ProductResponse
    @Mock ProductService    productService;

    @InjectMocks
    SellerService sellerService;

    // Создаёт пользователя с ролью SELLER
    private User makeSeller(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("seller" + id + "@example.com");
        u.setFullName("Seller " + id);
        u.setShopName("Shop " + id);
        u.setBalance(BigDecimal.ZERO);
        u.setRole(Role.SELLER);
        return u;
    }

    // Создаёт пользователя с ролью CLIENT (не продавец)
    private User makeClient(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    // Создаёт продукт, привязанный к продавцу
    private Product makeProduct(Long id, User seller) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setPrice(new BigDecimal("1000.00"));
        p.setStockQuantity(5);
        p.setSeller(seller); // продавец — обязательное поле для проверки владения
        return p;
    }

    // Создаёт готовый DTO-ответ для мока productService.toResponse()
    private ProductResponse makeProductResponse(Long id, String name) {
        ProductResponse r = new ProductResponse();
        r.setId(id);
        r.setName(name);
        r.setPrice(new BigDecimal("1000.00"));
        r.setStockQuantity(5);
        return r;
    }

    // ── getSellerProducts ─────────────────────────────────────────────────────

    @Test
    void getSellerProducts_returnsMappedProducts() {
        User seller = makeSeller(3L);
        Product p1 = makeProduct(1L, seller);
        Product p2 = makeProduct(2L, seller);
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));
        when(productRepository.findBySeller(seller)).thenReturn(List.of(p1, p2));
        // Настраиваем мок: при вызове toResponse для конкретного продукта вернуть нужный DTO
        when(productService.toResponse(p1)).thenReturn(makeProductResponse(1L, "Product 1"));
        when(productService.toResponse(p2)).thenReturn(makeProductResponse(2L, "Product 2"));

        List<ProductResponse> result = sellerService.getSellerProducts(3L);

        assertThat(result).hasSize(2);
        // extracting — извлекает поле из каждого элемента списка для проверки
        assertThat(result).extracting(ProductResponse::getId).containsExactly(1L, 2L);
    }

    @Test
    void getSellerProducts_emptyList_returnsEmpty() {
        User seller = makeSeller(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));
        when(productRepository.findBySeller(seller)).thenReturn(List.of()); // нет товаров

        assertThat(sellerService.getSellerProducts(3L)).isEmpty();
    }

    @Test
    void getSellerProducts_sellerNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.getSellerProducts(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getSellerProducts_userIsNotSeller_throwsException() {
        // Клиент пытается вызвать метод продавца — должен получить ошибку
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeClient(1L)));

        assertThatThrownBy(() -> sellerService.getSellerProducts(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a seller");
    }

    // ── createProduct ─────────────────────────────────────────────────────────

    @Test
    void createProduct_savesProductLinkedToSeller() {
        User seller = makeSeller(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("New Gadget");
        req.setDescription("Nice gadget");
        req.setPrice(new BigDecimal("2500.00"));
        req.setStockQuantity(10);
        req.setImageUrl("http://img.example.com/gadget.jpg");

        Product saved = makeProduct(5L, seller);
        saved.setName("New Gadget");
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productService.toResponse(saved)).thenReturn(makeProductResponse(5L, "New Gadget"));

        ProductResponse result = sellerService.createProduct(3L, req);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getName()).isEqualTo("New Gadget");

        // ArgumentCaptor позволяет «поймать» объект, переданный в save, и проверить его поля.
        // Это нужно когда мы хотим проверить не просто факт вызова, а содержимое аргумента.
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product captured = captor.getValue(); // получаем объект, который был передан в save

        assertThat(captured.getSeller()).isEqualTo(seller); // товар привязан к правильному продавцу
        assertThat(captured.getName()).isEqualTo("New Gadget");
        assertThat(captured.getPrice()).isEqualByComparingTo("2500.00");
        assertThat(captured.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void createProduct_sellerNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.createProduct(99L, new CreateProductRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).save(any()); // save не должен вызываться
    }

    @Test
    void createProduct_userIsNotSeller_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeClient(1L)));

        assertThatThrownBy(() -> sellerService.createProduct(1L, new CreateProductRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a seller");
    }

    // ── updateProduct ─────────────────────────────────────────────────────────

    @Test
    void updateProduct_updatesAllFields() {
        User seller = makeSeller(3L);
        Product product = makeProduct(1L, seller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Updated Name");
        req.setDescription("Updated description");
        req.setPrice(new BigDecimal("3000.00"));
        req.setStockQuantity(20);
        req.setImageUrl("http://new-img.jpg");

        when(productRepository.save(product)).thenReturn(product);
        when(productService.toResponse(product)).thenReturn(makeProductResponse(1L, "Updated Name"));

        ProductResponse result = sellerService.updateProduct(3L, 1L, req);

        assertThat(result.getName()).isEqualTo("Updated Name");
        // Проверяем что поля объекта product действительно изменились (сервис мутирует объект)
        assertThat(product.getDescription()).isEqualTo("Updated description");
        assertThat(product.getPrice()).isEqualByComparingTo("3000.00");
        assertThat(product.getStockQuantity()).isEqualTo(20);
        assertThat(product.getImageUrl()).isEqualTo("http://new-img.jpg");
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_productNotFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.updateProduct(3L, 99L, new CreateProductRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateProduct_productBelongsToAnotherSeller_throwsException() {
        // Товар принадлежит продавцу id=4, а запрос приходит от продавца id=3
        User otherSeller = makeSeller(4L);
        Product product = makeProduct(1L, otherSeller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Продавец id=3 не должен редактировать чужой товар
        assertThatThrownBy(() -> sellerService.updateProduct(3L, 1L, new CreateProductRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void updateProduct_productWithNullSeller_throwsException() {
        // Товар без продавца (seller=null) — тоже не принадлежит продавцу id=3
        Product product = makeProduct(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> sellerService.updateProduct(3L, 1L, new CreateProductRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    // ── deleteProduct ─────────────────────────────────────────────────────────

    @Test
    void deleteProduct_deletesSuccessfully() {
        User seller = makeSeller(3L);
        Product product = makeProduct(1L, seller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        sellerService.deleteProduct(3L, 1L);

        // Удаляем по объекту (а не по id) — так сервис проверяет владельца перед удалением
        verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_productNotFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.deleteProduct(3L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteProduct_productBelongsToAnotherSeller_throwsException() {
        User otherSeller = makeSeller(4L);
        Product product = makeProduct(1L, otherSeller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> sellerService.deleteProduct(3L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        verify(productRepository, never()).delete(any(Product.class));
    }

    // ── getBalance ────────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsFullSellerInfo() {
        User seller = makeSeller(3L);
        seller.setBalance(new BigDecimal("15000.50")); // устанавливаем текущий баланс
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));

        SellerResponse result = sellerService.getBalance(3L);

        // SellerResponse содержит все публичные данные продавца + его баланс
        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getEmail()).isEqualTo("seller3@example.com");
        assertThat(result.getFullName()).isEqualTo("Seller 3");
        assertThat(result.getShopName()).isEqualTo("Shop 3");
        assertThat(result.getBalance()).isEqualByComparingTo("15000.50");
    }

    @Test
    void getBalance_sellerNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.getBalance(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getBalance_userIsNotSeller_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeClient(1L)));

        assertThatThrownBy(() -> sellerService.getBalance(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a seller");
    }

    // ── getSellerOrders ───────────────────────────────────────────────────────

    @Test
    void getSellerOrders_returnsOrdersFromRepository() {
        User seller = makeSeller(3L);
        Order o1 = new Order(); o1.setId(1L);
        Order o2 = new Order(); o2.setId(2L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));
        // findBySellerId — кастомный JPQL-запрос: заказы, содержащие товары этого продавца
        when(orderRepository.findBySellerId(3L)).thenReturn(List.of(o1, o2));

        List<Order> result = sellerService.getSellerOrders(3L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Order::getId).containsExactly(1L, 2L);
    }

    @Test
    void getSellerOrders_noOrders_returnsEmptyList() {
        User seller = makeSeller(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));
        when(orderRepository.findBySellerId(3L)).thenReturn(List.of());

        assertThat(sellerService.getSellerOrders(3L)).isEmpty();
    }

    @Test
    void getSellerOrders_userIsNotSeller_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeClient(1L)));

        assertThatThrownBy(() -> sellerService.getSellerOrders(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a seller");

        // Если пользователь не продавец, до репозитория дойти не должно
        verify(orderRepository, never()).findBySellerId(any());
    }
}
