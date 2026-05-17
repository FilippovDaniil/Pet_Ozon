package com.example.marketplace.repository;

import com.example.marketplace.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

// Репозиторий для работы с таблицей email_logs.
// JpaRepository<EmailLog, Long> — первый параметр: сущность, второй: тип первичного ключа.
// Spring Data автоматически реализует этот интерфейс: стандартные CRUD-методы
// (save, findById, findAll, delete и др.) генерируются во время сборки.
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // Spring Data разбирает имя метода по соглашению: countBy + имя поля + аргумент.
    // Генерирует SQL: SELECT COUNT(*) FROM email_logs WHERE success = ?
    // Используется в AccountantService.getSummary() для подсчёта успешных доставок
    long countBySuccess(boolean success);

    // Переопределяем стандартный findAll() с параметром Sort.
    // JpaRepository наследует этот метод от ListCrudRepository, но возвращает List напрямую —
    // объявление здесь делает сигнатуру явной и позволяет удобно вызывать:
    // emailLogRepository.findAll(Sort.by(Direction.DESC, "sentAt"))
    List<EmailLog> findAll(Sort sort);
}
