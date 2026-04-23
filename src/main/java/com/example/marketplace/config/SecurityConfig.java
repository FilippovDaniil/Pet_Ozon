package com.example.marketplace.config;

import com.example.marketplace.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Центральная конфигурация Spring Security.
 *
 * @EnableWebSecurity   — включает интеграцию Spring Security с Spring MVC.
 * @EnableMethodSecurity — позволяет использовать @PreAuthorize, @PostAuthorize
 *                        на методах сервисов/контроллеров.
 *
 * Главный концепт: SecurityFilterChain — цепочка фильтров, которые
 * обрабатывают каждый HTTP-запрос до того, как он попадёт в контроллер.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Настройка правил доступа к эндпоинтам.
     *
     * Читать сверху вниз: первое совпадающее правило побеждает.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF-защита не нужна для REST API со stateless аутентификацией.
                .csrf(AbstractHttpConfigurer::disable)

                // Включаем CORS с настройками из CorsConfig.
                .cors(Customizer.withDefaults())

                // STATELESS: сервер не создаёт HTTP-сессии, аутентификация — только через JWT.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Статические файлы фронтенда — без аутентификации.
                        .requestMatchers("/*.html", "/js/**", "/css/**", "/images/**").permitAll()

                        // Регистрация и вход — без токена (очевидно).
                        .requestMatchers("/api/auth/**").permitAll()

                        // Просмотр товаров — публичный доступ (не нужно логиниться).
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()

                        // Админские эндпоинты — только для роли ADMIN.
                        // Spring добавит "ROLE_" автоматически: ищет "ROLE_ADMIN".
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Эндпоинты продавца — только для роли SELLER.
                        .requestMatchers("/api/seller/**").hasRole("SELLER")

                        // Все остальные запросы требуют любой аутентификации.
                        .anyRequest().authenticated()
                )

                // Регистрируем наш AuthenticationProvider.
                .authenticationProvider(authenticationProvider())

                // Наш JWT-фильтр ставим ПЕРЕД стандартным фильтром логина.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider — стандартный провайдер аутентификации Spring.
     * Он знает, как: загрузить пользователя (через UserDetailsService)
     * и сравнить введённый пароль с хэшем (через PasswordEncoder).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager нужен в AuthController для явной проверки логин/пароль
     * при вызове POST /api/auth/login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt — безопасный алгоритм хэширования паролей.
     * Каждый раз добавляет случайную «соль» — одинаковые пароли дают разные хэши.
     * Стойкость регулируется «cost factor» (по умолчанию 10 итераций).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
