package com.example.marketplace.controller;

import com.example.marketplace.dto.request.LoginRequest;
import com.example.marketplace.dto.request.RegisterRequest;
import com.example.marketplace.dto.response.AuthResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.security.JwtUtil;
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
 * @RestController = @Controller + @ResponseBody: методы возвращают JSON автоматически.
 * @RequestMapping — общий префикс URL для всех методов этого контроллера.
 *
 * Как работает логин:
 *   1. authenticationManager.authenticate() — Spring Security проверяет email/пароль.
 *      Если неверно — выбрасывает AuthenticationException → GlobalExceptionHandler вернёт 401.
 *   2. Загружаем User из БД.
 *   3. Генерируем JWT-токен через JwtUtil.
 *   4. Возвращаем токен в теле И в заголовке Authorization.
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

    /**
     * POST /api/auth/login — вход в систему.
     * @Valid запускает валидацию LoginRequest (@NotBlank, @Email).
     * ResponseEntity позволяет управлять статусом ответа и заголовками.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Делегируем проверку Spring Security. При ошибке — автоматически 401.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        // Если аутентификация прошла — загружаем полный объект пользователя.
        User user = (User) userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(user);

        log.info("ACTION=LOGIN userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());

        // Токен отдаём и в теле ответа (для удобства), и в заголовке (стандарт).
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name(), user.getFullName(), user.getShopName()));
    }

    /**
     * POST /api/auth/register — регистрация нового покупателя.
     * Статус 201 Created — стандартный HTTP-ответ при создании нового ресурса.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerClient(request.getEmail(), request.getPassword(), request.getFullName());
        String token = jwtUtil.generateToken(user);

        log.info("ACTION=REGISTER userId={} email={}", user.getId(), user.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name(), user.getFullName(), user.getShopName()));
    }
}
