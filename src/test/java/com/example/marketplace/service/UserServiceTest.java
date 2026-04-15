package com.example.marketplace.service;

import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("client@example.com");
        assertThat(result.getRole()).isEqualTo(Role.CLIENT);
    }

    @Test
    void getById_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 99");
    }

    @Test
    void getById_adminUser_returnsCorrectRole() {
        User admin = makeUser(2L, "admin@example.com", Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        User result = userService.getById(2L);

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
}
