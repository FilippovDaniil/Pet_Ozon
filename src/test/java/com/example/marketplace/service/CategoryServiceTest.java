package com.example.marketplace.service;

import com.example.marketplace.entity.Category;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты CategoryService: CRUD категорий.
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService categoryService;

    private Category cat(Long id, String name) {
        Category c = new Category(name);
        try {
            var f = Category.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsAlphabeticallySortedList() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                cat(2L, "Ноутбуки"),
                cat(3L, "Аудио"),
                cat(1L, "Смартфоны")
        ));

        List<Category> result = categoryService.getAll();

        // После сортировки: Аудио < Ноутбуки < Смартфоны (без учёта регистра).
        assertThat(result).extracting(Category::getName)
                .containsExactly("Аудио", "Ноутбуки", "Смартфоны");
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        assertThat(categoryService.getAll()).isEmpty();
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_existingId_returnsCategory() {
        Category audio = cat(1L, "Аудио");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(audio));

        Category result = categoryService.getById(1L);

        assertThat(result.getName()).isEqualTo("Аудио");
    }

    @Test
    void getById_unknownId_throwsResourceNotFoundException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findOrCreate ──────────────────────────────────────────────────────────

    @Test
    void findOrCreate_existingCategory_returnsExisting() {
        Category existing = cat(1L, "Аудио");
        when(categoryRepository.findByName("Аудио")).thenReturn(Optional.of(existing));

        Category result = categoryService.findOrCreate("Аудио");

        assertThat(result).isSameAs(existing);
        // save не должен вызываться — категория уже есть.
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findOrCreate_newCategory_createsAndReturns() {
        when(categoryRepository.findByName("Другое")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.findOrCreate("Другое");

        assertThat(result.getName()).isEqualTo("Другое");
        verify(categoryRepository).save(any());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_newName_savesAndReturns() {
        when(categoryRepository.existsByName("Планшеты")).thenReturn(false);
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.create("Планшеты");

        assertThat(result.getName()).isEqualTo("Планшеты");
        verify(categoryRepository).save(any());
    }

    @Test
    void create_duplicateName_throwsIllegalArgumentException() {
        when(categoryRepository.existsByName("Аудио")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create("Аудио"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Аудио");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Category audio = cat(1L, "Аудио");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(audio));

        categoryService.delete(1L);

        verify(categoryRepository).delete(audio);
    }

    @Test
    void delete_unknownId_throwsResourceNotFoundException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
