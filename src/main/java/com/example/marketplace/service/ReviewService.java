package com.example.marketplace.service;

import com.example.marketplace.dto.response.ReviewResponse;
import com.example.marketplace.entity.Product;
import com.example.marketplace.entity.Review;
import com.example.marketplace.entity.User;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис отзывов и рейтингов.
 *
 * Главное бизнес-правило: один пользователь — один отзыв на один товар.
 * Нарушение этого правила обрабатывается на двух уровнях:
 *   1. Проверка в методе addReview() — явное исключение с понятным сообщением.
 *   2. Уникальный составной индекс в БД (uk_review_product_user) — последний барьер.
 *
 * При добавлении отзыва инвалидируем кэш "products" для этого товара,
 * так как поля averageRating и reviewCount в ProductResponse изменятся.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductService   productService;

    /**
     * Возвращает все отзывы на конкретный товар.
     *
     * readOnly = true — подсказка Hibernate не отслеживать изменения объектов.
     * Для метода чтения это небольшая оптимизация производительности.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getProductReviews(Long productId) {
        Product product = productService.findEntityById(productId);
        return reviewRepository.findByProduct(product).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Добавляет новый отзыв от текущего пользователя.
     *
     * Проверяет, что пользователь ещё не оставлял отзыв на этот товар.
     * isPresent() — если Optional не пустой, отзыв уже есть → бросаем ошибку.
     *
     * @CacheEvict(value = "products", key = "#productId") — после добавления отзыва
     * удаляем закэшированный ответ для этого товара: averageRating изменился.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public ReviewResponse addReview(Long productId, User author, int rating, String comment) {
        Product product = productService.findEntityById(productId);

        // Бизнес-правило: нельзя оставить два отзыва на один товар
        if (reviewRepository.findByProductAndUser(product, author).isPresent()) {
            throw new IllegalArgumentException(
                    "Вы уже оставили отзыв на товар «" + product.getName() + "»");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setUser(author);
        review.setRating(rating);
        review.setComment(comment);
        Review saved = reviewRepository.save(review);

        log.info("ACTION=ADD_REVIEW userId={} productId={} rating={}", author.getId(), productId, rating);
        return toResponse(saved);
    }

    /**
     * Конвертирует Review → ReviewResponse.
     *
     * Заполняем только нужные поля из связанных сущностей (user.id, user.fullName).
     * Полный объект User не отдаём — он содержит password и другие приватные данные.
     */
    private ReviewResponse toResponse(Review review) {
        ReviewResponse r = new ReviewResponse();
        r.setId(review.getId());
        r.setUserId(review.getUser().getId());
        // getFullName() может быть null (поле необязательное у User) → показываем email
        String name = review.getUser().getFullName();
        r.setUserFullName(name != null ? name : review.getUser().getEmail());
        r.setRating(review.getRating());
        r.setComment(review.getComment());
        r.setCreatedAt(review.getCreatedAt());
        return r;
    }
}
