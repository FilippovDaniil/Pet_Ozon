package com.example.marketplace.controller;

import com.example.marketplace.dto.request.CreateReviewRequest;
import com.example.marketplace.dto.response.ReviewResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер отзывов на товары.
 *
 * GET  /api/products/{productId}/reviews       — список отзывов (публичный)
 * POST /api/products/{productId}/reviews       — оставить отзыв (авторизованный)
 *
 * Почему URL вложенный (/products/{id}/reviews)?
 * REST-принцип: отзывы — это «коллекция внутри товара», поэтому используем
 * иерархический URL. Это лучше, чем /api/reviews?productId=5 —
 * такой вариант менее читаем и смешивает разные уровни абстракции.
 */
@RestController
@RequestMapping(value = "/api/products/{productId}/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * GET /api/products/{productId}/reviews — все отзывы на товар.
     *
     * Публичный эндпоинт — можно посмотреть отзывы без регистрации.
     * Настроено в SecurityConfig: .requestMatchers(GET, "/api/products/**").permitAll()
     *
     * @PathVariable Long productId — id товара из URL (/api/products/3/reviews → productId=3).
     */
    @GetMapping
    public List<ReviewResponse> getReviews(@PathVariable Long productId) {
        return reviewService.getProductReviews(productId);
    }

    /**
     * POST /api/products/{productId}/reviews — оставить отзыв.
     *
     * Требует аутентификации (JWT-токен в заголовке Authorization).
     * @AuthenticationPrincipal User user — Spring Security достаёт текущего пользователя
     * из SecurityContext (туда его поместил JwtAuthenticationFilter).
     *
     * @ResponseStatus(CREATED) — возвращаем HTTP 201, а не 200.
     * Стандарт REST: создание ресурса → 201 Created.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse addReview(
            @PathVariable Long productId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.addReview(
                productId,
                user,
                request.getRating(),
                request.getComment()
        );
    }
}
