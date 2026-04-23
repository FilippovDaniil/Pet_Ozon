package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Стандартный ответ при ошибках — возвращается из GlobalExceptionHandler.
 *
 * Единый формат ошибок во всём API:
 *   {
 *     "timestamp": "2025-04-23T12:34:56",
 *     "message": "Product not found with id: 99",
 *     "status": 404
 *   }
 *
 * @AllArgsConstructor — конструктор с тремя параметрами используется в GlobalExceptionHandler:
 *   return new ErrorResponse(LocalDateTime.now(), ex.getMessage(), 404);
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;  // когда произошла ошибка
    private String message;           // человекочитаемое описание
    private int status;               // HTTP-статус (дублирует код ответа для удобства)
}
