package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.ReviewResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты ReviewController.
 *
 * Два типа эндпоинтов:
 *   GET  /api/products/{id}/reviews — публичный (без токена, разрешён по SecurityConfig)
 *   POST /api/products/{id}/reviews — требует аутентификации
 *
 * Почему GET публичный? SecurityConfig содержит:
 *   .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
 * «**» матчит любые подпути, в том числе /api/products/1/reviews.
 */
@WebMvcTest(
        value = ReviewController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ReviewService reviewService;

    // Вспомогательный метод: создаёт ReviewResponse для подстановки в мок
    private ReviewResponse makeReviewResponse(Long id, int rating, String comment, String author) {
        ReviewResponse r = new ReviewResponse();
        r.setId(id);
        r.setUserId(1L);
        r.setUserFullName(author);
        r.setRating(rating);
        r.setComment(comment);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    // Вспомогательный метод: аутентифицированный пользователь для .with(user(...))
    private User mockClientUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    // ── GET /api/products/{productId}/reviews ─────────────────────────────────

    @Test
    void getReviews_publicEndpoint_returns200WithList() throws Exception {
        // GET /api/products/** — публичный: запрос БЕЗ токена должен работать
        when(reviewService.getProductReviews(1L)).thenReturn(List.of(
                makeReviewResponse(1L, 5, "Отличный!", "Иван"),
                makeReviewResponse(2L, 3, "Нормально", "Мария")
        ));

        // Запрос без .with(user(...)) — имитируем анонимного пользователя
        mockMvc.perform(get("/api/products/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Отличный!"))
                .andExpect(jsonPath("$[0].userFullName").value("Иван"))
                .andExpect(jsonPath("$[1].rating").value(3));
    }

    @Test
    void getReviews_noReviews_returns200WithEmptyList() throws Exception {
        when(reviewService.getProductReviews(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/products/2/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getReviews_productNotFound_returns404() throws Exception {
        when(reviewService.getProductReviews(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99/reviews"))
                .andExpect(status().isNotFound())
                // GlobalExceptionHandler возвращает ErrorResponse {status, message}
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    // ── POST /api/products/{productId}/reviews ────────────────────────────────

    @Test
    void addReview_validRequest_returns201() throws Exception {
        ReviewResponse response = makeReviewResponse(1L, 4, "Хорошая мышь", "Алексей");
        // eq(1L) — productId; any(User.class) — пользователь из SecurityContext; eq(4) — рейтинг
        when(reviewService.addReview(eq(1L), any(User.class), eq(4), eq("Хорошая мышь")))
                .thenReturn(response);

        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser())) // аутентифицированный пользователь
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4, \"comment\": \"Хорошая мышь\"}"))
                .andExpect(status().isCreated()) // HTTP 201 Created
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Хорошая мышь"))
                .andExpect(jsonPath("$.userFullName").value("Алексей"));
    }

    @Test
    void addReview_withoutComment_returns201() throws Exception {
        // comment необязательное: можно передать только рейтинг
        ReviewResponse response = makeReviewResponse(1L, 5, null, "Иван");
        when(reviewService.addReview(eq(1L), any(User.class), eq(5), isNull()))
                .thenReturn(response);

        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 5}")) // comment отсутствует
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void addReview_ratingTooHigh_returns400() throws Exception {
        // rating = 6 нарушает @Max(5) в CreateReviewRequest → GlobalExceptionHandler → 400
        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 6}"))
                .andExpect(status().isBadRequest());

        // Сервис не должен вызываться — валидация срабатывает раньше
        verify(reviewService, never()).addReview(any(), any(), anyInt(), any());
    }

    @Test
    void addReview_ratingZero_returns400() throws Exception {
        // rating = 0 нарушает @Min(1) → 400
        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 0}"))
                .andExpect(status().isBadRequest());

        verify(reviewService, never()).addReview(any(), any(), anyInt(), any());
    }

    @Test
    void addReview_ratingNull_returns400() throws Exception {
        // @NotNull(message = "Оценка обязательна") — rating пропущен → 400
        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"Без оценки\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addReview_unauthenticated_returns401() throws Exception {
        // Без токена и без .with(user(...)) — 401 Unauthorized
        mockMvc.perform(post("/api/products/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4}"))
                .andExpect(status().isUnauthorized());

        verify(reviewService, never()).addReview(any(), any(), anyInt(), any());
    }

    @Test
    void addReview_duplicateReview_returns400() throws Exception {
        // IllegalArgumentException → GlobalExceptionHandler → 400
        when(reviewService.addReview(eq(1L), any(User.class), eq(4), any()))
                .thenThrow(new IllegalArgumentException("Вы уже оставили отзыв"));

        mockMvc.perform(post("/api/products/1/reviews")
                        .with(user(mockClientUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4, \"comment\": \"Повтор\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Вы уже оставили отзыв"));
    }
}
