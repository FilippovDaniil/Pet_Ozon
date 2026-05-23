package com.example.marketplace.controller;

import com.example.marketplace.dto.request.LoginRequest;
import com.example.marketplace.dto.request.RefreshTokenRequest;
import com.example.marketplace.dto.request.RegisterRequest;
import com.example.marketplace.dto.response.AuthResponse;
import com.example.marketplace.entity.RefreshToken;
import com.example.marketplace.entity.User;
import com.example.marketplace.security.JwtUtil;
import com.example.marketplace.service.RefreshTokenService;
import com.example.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации — публичные эндпоинты, доступные без токена.
 *
 * Схема работы с токенами:
 *   1. POST /api/auth/login → возвращает access (15 мин) + refresh (7 дней).
 *   2. Клиент отправляет access в каждом запросе: Authorization: Bearer <token>.
 *   3. Когда access истекает (получает 401), клиент автоматически вызывает
 *      POST /api/auth/refresh → получает новую пару токенов (ротация).
 *   4. При логауте POST /api/auth/logout инвалидирует refresh-токен в БД.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    /**
     * POST /api/auth/login — вход в систему.
     * Возвращает access-токен (15 мин) и refresh-токен (7 дней).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = (User) userDetailsService.loadUserByUsername(request.getEmail());
        String accessToken = jwtUtil.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("ACTION=LOGIN userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new AuthResponse(
                        accessToken, refreshToken.getToken(),
                        user.getId(), user.getEmail(), user.getRole().name(),
                        user.getFullName(), user.getShopName()));
    }

    /**
     * POST /api/auth/register — регистрация нового покупателя.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerClient(request.getEmail(), request.getPassword(), request.getFullName());
        String accessToken = jwtUtil.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("ACTION=REGISTER userId={} email={}", user.getId(), user.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new AuthResponse(
                        accessToken, refreshToken.getToken(),
                        user.getId(), user.getEmail(), user.getRole().name(),
                        user.getFullName(), user.getShopName()));
    }

    /**
     * POST /api/auth/refresh — обновление access-токена по refresh-токену.
     *
     * Ротация: старый refresh-токен удаляется, новая пара (access + refresh) создаётся.
     * Это защищает от повторного использования перехваченного refresh-токена.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken oldRefresh = refreshTokenService.findValid(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh-токен недействителен или истёк"));

        User user = oldRefresh.getUser();

        // Ротация: удаляем старый токен и создаём новую пару.
        refreshTokenService.delete(oldRefresh);
        String newAccessToken = jwtUtil.generateToken(user);
        RefreshToken newRefresh = refreshTokenService.createRefreshToken(user);

        log.info("ACTION=REFRESH userId={}", user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                .body(new AuthResponse(
                        newAccessToken, newRefresh.getToken(),
                        user.getId(), user.getEmail(), user.getRole().name(),
                        user.getFullName(), user.getShopName()));
    }

    /**
     * POST /api/auth/logout — инвалидация refresh-токена (клиент должен передать его).
     * После этого ни старый access, ни refresh повторно не будут работать для обновления.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.findValid(request.getRefreshToken())
                .ifPresent(t -> {
                    log.info("ACTION=LOGOUT userId={}", t.getUser().getId());
                    refreshTokenService.delete(t);
                });
        return ResponseEntity.noContent().build();
    }
}
