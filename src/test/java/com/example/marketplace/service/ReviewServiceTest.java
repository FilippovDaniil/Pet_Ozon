package com.example.marketplace.service;

import com.example.marketplace.dto.response.ReviewResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.Review;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты ReviewService: отзывы, рейтинги, бизнес-правила.
// ReviewService зависит от ReviewRepository и ProductService — оба мокируем.
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    // ProductService мокируем, а не его зависимости: нам важно поведение findEntityById,
    // а не внутренняя реализация. Это изолирует ReviewService от изменений в ProductService.
    @Mock ProductService   productService;

    @InjectMocks ReviewService reviewService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Product makeProduct(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("9999.00"));
        return p;
    }

    private User makeUser(Long id, String fullName) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setFullName(fullName);
        u.setRole(Role.CLIENT);
        return u;
    }

    private Review makeReview(Long id, Product product, User user, int rating, String comment) {
        Review r = new Review();
        r.setId(id);
        r.setProduct(product);
        r.setUser(user);
        r.setRating(rating);
        r.setComment(comment);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    // ── getProductReviews ─────────────────────────────────────────────────────

    @Test
    void getProductReviews_returnsListOfResponses() {
        Product product = makeProduct(1L, "Ноутбук");
        User user1 = makeUser(1L, "Иван");
        User user2 = makeUser(2L, "Мария");

        when(productService.findEntityById(1L)).thenReturn(product);
        when(reviewRepository.findByProduct(product)).thenReturn(List.of(
                makeReview(1L, product, user1, 5, "Отличный!"),
                makeReview(2L, product, user2, 3, "Средне")
        ));

        List<ReviewResponse> result = reviewService.getProductReviews(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRating()).isEqualTo(5);
        assertThat(result.get(0).getComment()).isEqualTo("Отличный!");
        assertThat(result.get(0).getUserFullName()).isEqualTo("Иван");
        assertThat(result.get(1).getRating()).isEqualTo(3);
    }

    @Test
    void getProductReviews_noReviews_returnsEmptyList() {
        Product product = makeProduct(1L, "Ноутбук");
        when(productService.findEntityById(1L)).thenReturn(product);
        when(reviewRepository.findByProduct(product)).thenReturn(List.of());

        List<ReviewResponse> result = reviewService.getProductReviews(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getProductReviews_productNotFound_throwsException() {
        // productService.findEntityById бросает ResourceNotFoundException — ReviewService не должен его проглатывать
        when(productService.findEntityById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        assertThatThrownBy(() -> reviewService.getProductReviews(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── addReview ─────────────────────────────────────────────────────────────

    @Test
    void addReview_success_createsAndReturnsReview() {
        Product product = makeProduct(1L, "Мышь");
        User author = makeUser(1L, "Алексей");

        when(productService.findEntityById(1L)).thenReturn(product);
        // Optional.empty() — пользователь ещё НЕ оставлял отзыв на этот товар
        when(reviewRepository.findByProductAndUser(product, author)).thenReturn(Optional.empty());

        // thenAnswer: при сохранении возвращаем тот же объект + проставляем id и время
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review saved = inv.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        ReviewResponse result = reviewService.addReview(1L, author, 4, "Хорошая мышь");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getComment()).isEqualTo("Хорошая мышь");
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUserFullName()).isEqualTo("Алексей");
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void addReview_withoutComment_isAllowed() {
        // comment — необязательное поле, null допустим
        Product product = makeProduct(1L, "Мышь");
        User author = makeUser(1L, "Алексей");

        when(productService.findEntityById(1L)).thenReturn(product);
        when(reviewRepository.findByProductAndUser(product, author)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReviewResponse result = reviewService.addReview(1L, author, 5, null);

        assertThat(result.getComment()).isNull();
        assertThat(result.getRating()).isEqualTo(5);
    }

    @Test
    void addReview_duplicateReview_throwsIllegalArgument() {
        // Бизнес-правило: один пользователь — один отзыв на товар.
        // Если findByProductAndUser возвращает непустой Optional — исключение.
        Product product = makeProduct(1L, "Ноутбук");
        User author = makeUser(1L, "Иван");
        Review existingReview = makeReview(1L, product, author, 3, "Уже оставлял");

        when(productService.findEntityById(1L)).thenReturn(product);
        // isPresent() вернёт true → сервис должен бросить исключение
        when(reviewRepository.findByProductAndUser(product, author)).thenReturn(Optional.of(existingReview));

        assertThatThrownBy(() -> reviewService.addReview(1L, author, 5, "Снова пишу"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уже оставили отзыв");

        // save не должен вызываться: проверка провалилась до сохранения
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void addReview_productNotFound_throwsException() {
        User author = makeUser(1L, "Иван");
        when(productService.findEntityById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        assertThatThrownBy(() -> reviewService.addReview(99L, author, 4, "Текст"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void addReview_userWithoutFullName_usesEmailAsFallback() {
        // getFullName() может быть null — тогда в ответе используем email.
        // Это важно: ProductResponse.userFullName никогда не должен быть null.
        Product product = makeProduct(1L, "Клавиатура");
        User author = makeUser(1L, null); // fullName не задан
        // email = "user1@test.com" из makeUser

        when(productService.findEntityById(1L)).thenReturn(product);
        when(reviewRepository.findByProductAndUser(product, author)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReviewResponse result = reviewService.addReview(1L, author, 4, null);

        // fullName == null → используем email
        assertThat(result.getUserFullName()).isEqualTo("user1@test.com");
    }
}
