package com.example.marketplace.exception;

/**
 * Исключение «ресурс не найден» — аналог HTTP 404 Not Found.
 *
 * Расширяет RuntimeException (unchecked exception) — не нужно объявлять
 * в сигнатуре метода через throws. Это стандартная практика для Spring-приложений.
 *
 * Обрабатывается в GlobalExceptionHandler.handleNotFound() и конвертируется
 * в JSON-ответ с HTTP 404:
 *   {"timestamp": "...", "message": "...", "status": 404}
 *
 * Пример использования:
 *   throw new ResourceNotFoundException("Product not found with id: " + id);
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
