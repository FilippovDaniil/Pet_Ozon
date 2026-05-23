package com.example.marketplace.controller;

import com.example.marketplace.entity.Category;
import com.example.marketplace.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Эндпоинты для работы с категориями.
 *
 * GET  /api/categories        — публичный список категорий (для фильтра каталога).
 * POST /api/categories        — создать категорию (только ADMIN).
 * DELETE /api/categories/{id} — удалить категорию (только ADMIN).
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** Список всех категорий — используется фронтом для заполнения дропдауна. */
    @GetMapping
    public List<Category> getAll() {
        return categoryService.getAll();
    }

    /** Создание категории — только ADMIN (защита дублируется в @PreAuthorize сервиса). */
    @PostMapping
    public ResponseEntity<Category> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Category created = categoryService.create(name.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Удаление категории — только ADMIN. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
