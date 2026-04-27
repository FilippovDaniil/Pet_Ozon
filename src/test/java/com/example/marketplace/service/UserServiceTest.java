package com.example.marketplace.service;

import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CartRepository;
import com.example.marketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Юнит-тесты для UserService: поиск пользователей, регистрация.
// Вместо реальной БД используются Mockito-моки.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    // PasswordEncoder — кодирует пароли (BCrypt). Мокируем, чтобы не запускать реальный BCrypt.
    @Mock PasswordEncoder passwordEncoder;

    // UserService создаётся с подставленными выше моками
    @InjectMocks UserService userService;

    // Вспомогательный метод: создаёт тестового пользователя с заданными параметрами
    private User makeUser(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPassword("pass");
        u.setRole(role);
        return u;
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsUser() {
        User user = makeUser(1L, "client@example.com", Role.CLIENT);
        // Настраиваем мок: при вызове findById(1) вернуть нашего пользователя
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getById(1L);

        // Проверяем все поля возвращённого объекта
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("client@example.com");
        assertThat(result.getRole()).isEqualTo(Role.CLIENT);
    }

    @Test
    void getById_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Проверяем что бросается правильное исключение с нужным сообщением
        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 99");
    }

    @Test
    void getById_adminUser_returnsCorrectRole() {
        User admin = makeUser(2L, "admin@example.com", Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        User result = userService.getById(2L);

        // Убеждаемся что роль ADMIN сохраняется корректно
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
    }

    // ── getByEmail ────────────────────────────────────────────────────────────

    @Test
    void getByEmail_found_returnsUser() {
        User user = makeUser(1L, "client@example.com", Role.CLIENT);
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));

        User result = userService.getByEmail("client@example.com");

        assertThat(result.getEmail()).isEqualTo("client@example.com");
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getByEmail_notFound_throwsException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByEmail("nobody@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nobody@example.com");
    }

    // ── registerClient ────────────────────────────────────────────────────────

    @Test
    void registerClient_newEmail_savesUserAndCart() {
        // existsByEmail возвращает false → email свободен, можно регистрировать
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        // Мок энкодера: реальный BCrypt не запускается, возвращаем фиктивный хеш
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pass");
        User saved = makeUser(10L, "new@example.com", Role.CLIENT);
        // any(User.class) — принять любой объект User при вызове save
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(cartRepository.save(any())).thenReturn(null);

        User result = userService.registerClient("new@example.com", "password123", "Новый");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getRole()).isEqualTo(Role.CLIENT);
        // Проверяем что оба save были вызваны: пользователь и его корзина
        verify(userRepository).save(any(User.class));
        verify(cartRepository).save(any());
    }

    @Test
    void registerClient_duplicateEmail_throwsException() {
        // existsByEmail возвращает true → email уже занят, регистрация должна быть запрещена
        when(userRepository.existsByEmail("client@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerClient("client@example.com", "pass", "Тест"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }
}
