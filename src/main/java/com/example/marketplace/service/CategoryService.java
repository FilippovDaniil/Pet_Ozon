package com.example.marketplace.service;

import com.example.marketplace.entity.Category;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления категориями.
 *
 * Чтение (getAll, getById) — доступно всем.
 * Изменение (create, delete) — только администратору.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** Список всех категорий, отсортированных по имени. */
    @Transactional(readOnly = true)
    public List<Category> getAll() {
        return categoryRepository.findAll()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Категория не найдена: " + id));
    }

    /** Возвращает существующую категорию или создаёт новую (используется при инициализации данных). */
    @Transactional
    public Category findOrCreate(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Category create(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new IllegalArgumentException("Категория уже существует: " + name);
        }
        return categoryRepository.save(new Category(name));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        Category cat = getById(id);
        categoryRepository.delete(cat);
    }
}
