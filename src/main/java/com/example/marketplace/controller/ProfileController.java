package com.example.marketplace.controller;

import com.example.marketplace.dto.request.UpdateProfileRequest;
import com.example.marketplace.dto.response.ProfileResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер профиля — текущий пользователь может просмотреть и обновить свои данные.
 *
 * Требует аутентификации. Доступен для любой роли (CLIENT, SELLER, ADMIN).
 *
 * Эндпоинты:
 *   GET   /api/profile/me  — просмотр профиля
 *   PATCH /api/profile/me  — частичное обновление (только переданные поля)
 *
 * Разница PUT vs PATCH:
 *   PUT   — полная замена ресурса (все поля обязательны).
 *   PATCH — частичное обновление (null-поля игнорируются в UserService.updateProfile).
 */
@RestController
@RequestMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    /**
     * GET /api/profile/me — возвращает профиль текущего пользователя.
     * User уже загружен Spring Security — обращаемся к БД не нужно.
     */
    @GetMapping("/me")
    public ProfileResponse getProfile(@AuthenticationPrincipal User user) {
        return toResponse(user);
    }

    /**
     * PATCH /api/profile/me — обновление профиля.
     * Конвертируем обновлённый User обратно в ProfileResponse.
     */
    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse updateProfile(@AuthenticationPrincipal User user,
                                          @RequestBody UpdateProfileRequest request) {
        return toResponse(userService.updateProfile(user.getId(), request));
    }

    /**
     * Конвертация User → ProfileResponse.
     * Вынесена в приватный метод, чтобы не дублировать в двух методах контроллера.
     * В большом проекте такую логику выносят в отдельный Mapper (MapStruct, ModelMapper).
     */
    private ProfileResponse toResponse(User user) {
        ProfileResponse r = new ProfileResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFullName(user.getFullName());
        r.setAddress(user.getAddress());
        r.setRole(user.getRole().name());
        r.setShopName(user.getShopName());
        r.setBalance(user.getBalance());
        return r;
    }
}
