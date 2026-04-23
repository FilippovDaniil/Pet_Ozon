package com.example.marketplace.security;

import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Мост между Spring Security и нашей базой данных.
 *
 * Spring Security не знает, как загружать пользователей — это зависит от приложения.
 * Интерфейс UserDetailsService с единственным методом loadUserByUsername —
 * это «контракт», который мы обязаны реализовать.
 *
 * В нашем случае "username" — это email пользователя.
 * Метод ищет пользователя в БД и возвращает его (User реализует UserDetails).
 *
 * Вызывается в двух местах:
 *   1. SecurityConfig.authenticationProvider() — при проверке логина/пароля.
 *   2. JwtAuthenticationFilter — при проверке JWT-токена.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // orElseThrow: если пользователь не найден — Spring Security получает исключение
        // и автоматически возвращает 401 Unauthorized.
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
