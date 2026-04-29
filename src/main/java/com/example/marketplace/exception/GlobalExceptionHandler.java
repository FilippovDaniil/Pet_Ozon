package com.example.marketplace.exception;

import com.example.marketplace.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений — единая точка конвертации Java-исключений в HTTP-ответы.
 *
 * @RestControllerAdvice — Spring перехватывает исключения из ВСЕХ контроллеров
 * и передаёт сюда. Без этого Spring вернул бы HTML-страницу с ошибкой.
 *
 * Как это работает:
 *   Контроллер выбрасывает исключение → Spring ищет @ExceptionHandler с нужным типом
 *   → вызывает найденный метод → метод возвращает ErrorResponse → Spring сериализует в JSON.
 *
 * Порядок обработчиков: Spring выбирает НАИБОЛЕЕ конкретный тип исключения.
 * Exception.class — самый общий, «ловит всё остальное» (fallback).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Ошибки валидации @Valid: поля не прошли проверку аннотаций (@NotBlank, @Min и т.д.).
     * Собираем все нарушения в одну строку: "email: Некорректный формат; password: обязателен".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ErrorResponse(LocalDateTime.now(), message, 400);
    }

    /** Ресурс не найден — наша кастомная исключение → 404 Not Found. */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 404);
    }

    /**
     * Некорректный аргумент — бросается в сервисах для бизнес-ошибок:
     * пустая корзина при checkout, повторная оплата, недостаток товара на складе.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException ex) {
        return new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 400);
    }

    /**
     * Ошибка аутентификации Spring Security — неверный логин/пароль.
     * AuthenticationException — базовый класс для BadCredentialsException и других.
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthentication(AuthenticationException ex) {
        return new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 401);
    }

    /**
     * Нет прав доступа — бросается @PreAuthorize при неудовлетворённом условии.
     * Например: пользователь с ролью CLIENT вызывает метод с @PreAuthorize("hasRole('ADMIN')").
     *
     * Важно: AccessDeniedException должен быть обработан явно, иначе Spring Security
     * перехватит его сам и вернёт свой HTML-ответ вместо нашего JSON ErrorResponse.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse(LocalDateTime.now(), "Доступ запрещён: недостаточно прав", 403);
    }

    /**
     * Клиент отправил не JSON или не указал Content-Type: application/json.
     * Возвращаем 415 Unsupported Media Type.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 415);
    }

    /**
     * Тело запроса не читается (битый JSON, неверный формат).
     * Возвращаем понятное сообщение вместо технического стектрейса.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new ErrorResponse(LocalDateTime.now(), "Malformed JSON request", 400);
    }

    /**
     * Fallback: любое непредвиденное исключение → 500 Internal Server Error.
     * В production не стоит раскрывать ex.getMessage() — это может утечь внутренняя информация.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        return new ErrorResponse(LocalDateTime.now(), "Internal error: " + ex.getMessage(), 500);
    }
}
