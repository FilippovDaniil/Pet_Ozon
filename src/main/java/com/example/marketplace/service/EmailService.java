package com.example.marketplace.service;

import com.example.marketplace.entity.EmailLog;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.OrderItem;
import com.example.marketplace.entity.User;
import com.example.marketplace.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Сервис отправки электронных писем через SMTP (Яндекс Почта).
// Отвечает за два сценария: чек покупателю после оплаты и произвольное письмо от администратора.
// Все попытки отправки (успешные и неуспешные) фиксируются в таблице email_logs.
@Slf4j       // Lombok: создаёт поле log = LoggerFactory.getLogger(EmailService.class)
@Service     // Помечает класс как Spring-компонент; бин регистрируется в контексте приложения
@RequiredArgsConstructor // Lombok: конструктор для final-полей; Spring инжектирует зависимости
public class EmailService {

    // JavaMailSender — Spring-обёртка над Jakarta Mail для отправки писем через SMTP
    private final JavaMailSender mailSender;

    // EmailLogRepository — сохраняем запись о каждой отправке в БД
    private final EmailLogRepository emailLogRepository;

    // @Value — инжектирует значение из application.properties (spring.mail.username).
    // В тестах это поле не устанавливается через @Value — там используется ReflectionTestUtils.
    @Value("${spring.mail.username}")
    private String from;

    // ── Публичный API ──────────────────────────────────────────────────────────

    /** HTML-чек покупателю после успешной оплаты. */
    public void sendOrderReceipt(User user, Order order, String paymentMethod) {
        // Формируем HTML-тело письма с деталями заказа
        String html = buildReceiptHtml(user, order, paymentMethod);
        // Вызываем внутренний метод отправки HTML; ошибки SMTP здесь поглощаются — не должны отменять оплату
        sendHtml(user.getEmail(), "Чек об оплате — Заказ #" + order.getId() + " · Marketplace", html);
    }

    /** Произвольное письмо от администратора через /api/admin/email/send. */
    public void sendCustomEmail(String to, String subject, String text) {
        try {
            // createMimeMessage() — создаём контейнер письма (стандарт MIME)
            MimeMessage msg = mailSender.createMimeMessage();
            // MimeMessageHelper упрощает работу с MimeMessage; false = не multipart (только текст)
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(from);      // отправитель: vorobyshek2015@yandex.ru из application.properties
            h.setTo(to);          // получатель из запроса администратора
            h.setSubject(subject);
            h.setText(text, false); // false = plain text (не HTML)
            mailSender.send(msg);
            log.info("ACTION=EMAIL_SENT to={} subject=\"{}\"", to, subject);
            // Сохраняем запись об успешной отправке в лог
            saveLog(to, subject, true, null);
        } catch (MailException | MessagingException e) {
            log.error("Email send failed to={}: {}", to, e.getMessage());
            // Сохраняем запись о сбое ПРЕЖДЕ чем бросать исключение — чтобы лог точно попал в БД
            saveLog(to, subject, false, e.getMessage());
            // В отличие от sendHtml, здесь исключение пробрасываем наружу:
            // администратор должен видеть ошибку; GlobalExceptionHandler вернёт 500
            throw new RuntimeException("Ошибка отправки письма: " + e.getMessage(), e);
        }
    }

    // ── Приватные методы ───────────────────────────────────────────────────────

    // Внутренний метод отправки HTML-письма с поглощением ошибок SMTP.
    // Используется для чека покупателю: сбой почты не должен откатывать транзакцию оплаты.
    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            // true = multipart (нужно для HTML-письма с возможными вложениями)
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true); // true = HTML-содержимое (не plain text)
            mailSender.send(msg);
            log.info("ACTION=RECEIPT_SENT to={}", to);
            // Успешная отправка — пишем в лог с success=true, ошибки нет
            saveLog(to, subject, true, null);
        } catch (MailException | MessagingException e) {
            // Сбой SMTP НЕ должен прерывать процесс оплаты — только предупреждаем в логах
            log.warn("Receipt email failed for to={}: {}", to, e.getMessage());
            // Фиксируем сбой в БД: бухгалтер увидит в отчёте что письмо не ушло
            saveLog(to, subject, false, e.getMessage());
            // Исключение НЕ пробрасываем — это принципиальное отличие от sendCustomEmail
        }
    }

    // Сохраняет запись о попытке отправки письма в таблицу email_logs.
    // Оборачивает операцию в try/catch: если БД тоже недоступна — только предупреждаем,
    // не бросаем исключение (чтобы не ломать основной поток выполнения).
    private void saveLog(String to, String subject, boolean success, String error) {
        try {
            EmailLog entry = new EmailLog();
            entry.setRecipient(to);
            entry.setSubject(subject);
            // LocalDateTime.now() — текущее время сервера (без часового пояса)
            entry.setSentAt(java.time.LocalDateTime.now());
            entry.setSuccess(success);
            // Усекаем сообщение об ошибке до 500 символов — ограничение столбца в БД (EmailLog.errorMessage)
            if (error != null) entry.setErrorMessage(error.length() > 500 ? error.substring(0, 500) : error);
            // Сохраняем запись: INSERT INTO email_logs (...)
            emailLogRepository.save(entry);
        } catch (Exception ex) {
            // Если сохранение лога само упало (БД недоступна) — только предупреждение, не ошибка
            log.warn("Failed to persist email log: {}", ex.getMessage());
        }
    }

    // ── Построение HTML-тела письма-чека ──────────────────────────────────────

    // Формирует красивый HTML-чек с таблицей товаров, итоговой суммой и деталями заказа.
    // Используется только для sendOrderReceipt; текст создаётся конкатенацией строк,
    // чтобы не добавлять шаблонизатор (Thymeleaf/Freemarker) как зависимость.
    private String buildReceiptHtml(User user, Order order, String paymentMethod) {
        // NumberFormat для форматирования денег в российском стиле: 89 999,99 ₽
        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));
        // DateTimeFormatter с русской локалью: "17 мая 2025, 14:30"
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", new Locale("ru"));
        String paidAt = LocalDateTime.now().format(dtf);

        // Отображаемое имя: fullName если заполнен, иначе email
        String customerName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() : user.getEmail();

        // Читаемое название способа оплаты с emoji-иконкой; default — сам код метода
        String methodLabel = switch (paymentMethod != null ? paymentMethod.toUpperCase() : "") {
            case "CARD"     -> "&#128179; Банковская карта";
            case "CASH"     -> "&#128181; Наличные";
            case "CRYPTO"   -> "&#8383; Криптовалюта";
            case "TRANSFER" -> "&#127974; Банковский перевод";
            default -> paymentMethod != null ? paymentMethod : "Карта";
        };

        // Строим строки таблицы товаров: Название | Кол-во | Цена | Сумма
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            String name = item.getProduct() != null ? item.getProduct().getName() : "Товар";
            // Стоимость позиции = цена на момент заказа × количество
            BigDecimal sub = item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()));
            rows.append("<tr>")
                .append(td(name, "left"))
                .append(td(String.valueOf(item.getQuantity()), "center"))
                .append(td(fmt.format(item.getPriceAtOrder()), "right"))
                .append(td("<b>" + fmt.format(sub) + "</b>", "right"))
                .append("</tr>");
        }

        // Адрес доставки с fallback-значением если не задан
        String address = order.getShippingAddress() != null ? order.getShippingAddress() : "Не указан";

        // Собираем полный HTML-документ: шапка с градиентом, детали заказа, таблица товаров, подвал
        return "<!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'></head>"
            + "<body style='font-family:Arial,sans-serif;background:#f3f4f6;margin:0;padding:24px'>"
            + "<div style='max-width:600px;margin:0 auto;background:white;border-radius:12px;"
            +             "overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1)'>"

            // ── Шапка с логотипом и приветствием
            + "<div style='background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:32px;text-align:center;color:white'>"
            + "<div style='font-size:44px;line-height:1.1;margin-bottom:10px'>&#128717;</div>"
            + "<h1 style='margin:0;font-size:26px;font-weight:800'>Marketplace</h1>"
            + "<p style='margin:8px 0 0;opacity:.85;font-size:15px'>Спасибо за покупку, " + esc(customerName) + "!</p>"
            + "</div>"

            // ── Детали заказа: номер, дата, способ оплаты, адрес
            + "<div style='padding:28px'>"
            + "<table style='width:100%;border-collapse:collapse;background:#f8fafc;border-radius:10px;margin-bottom:24px'>"
            + "<tbody>"
            + infoRow("Номер заказа", "#" + order.getId())
            + infoRow("Дата оплаты", paidAt)
            + infoRow("Способ оплаты", methodLabel)
            + infoRow("Адрес доставки", esc(address))
            + "</tbody></table>"

            // ── Таблица товаров из заказа
            + "<h3 style='color:#374151;margin:0 0 12px;font-size:16px;font-weight:700'>Состав заказа</h3>"
            + "<table style='width:100%;border-collapse:collapse;margin-bottom:20px'>"
            + "<thead><tr style='background:#f1f5f9'>"
            + th("Товар", "left") + th("Кол-во", "center") + th("Цена", "right") + th("Сумма", "right")
            + "</tr></thead>"
            + "<tbody>" + rows + "</tbody>"
            // ── Итоговая строка
            + "<tfoot><tr style='background:#f0fdf4'>"
            + "<td colspan='3' style='padding:14px 12px;font-size:15px;font-weight:700;color:#15803d'>Итого к оплате</td>"
            + "<td style='padding:14px 12px;font-size:17px;font-weight:800;color:#15803d;text-align:right'>"
            + fmt.format(order.getTotalAmount()) + "</td>"
            + "</tr></tfoot></table>"
            + "</div>"

            // ── Подвал с информацией о проекте
            + "<div style='background:#f8fafc;padding:18px;text-align:center;color:#9ca3af;"
            +             "font-size:12px;border-top:1px solid #e5e7eb'>"
            + "<p style='margin:0 0 4px'>Marketplace &middot; Spring Boot 3.4.4 &middot; Pet-проект</p>"
            + "<p style='margin:0'>По вопросам обращайтесь в службу поддержки</p>"
            + "</div>"
            + "</div></body></html>";
    }

    // Вспомогательный метод: строка таблицы деталей заказа (Ярлык | Значение)
    private String infoRow(String label, String value) {
        return "<tr>"
            + "<td style='padding:8px 14px;font-size:14px;color:#6b7280;border-bottom:1px solid #e5e7eb'>" + label + "</td>"
            + "<td style='padding:8px 14px;font-size:14px;font-weight:600;color:#111827;text-align:right;border-bottom:1px solid #e5e7eb'>" + value + "</td>"
            + "</tr>";
    }

    // Вспомогательный метод: заголовок столбца таблицы товаров
    private String th(String text, String align) {
        return "<th style='text-align:" + align + ";padding:10px 12px;font-size:11px;color:#6b7280;"
            + "text-transform:uppercase;letter-spacing:.5px'>" + text + "</th>";
    }

    // Вспомогательный метод: ячейка таблицы товаров
    private String td(String text, String align) {
        return "<td style='padding:11px 12px;border-bottom:1px solid #f1f5f9;font-size:14px;text-align:" + align + "'>" + text + "</td>";
    }

    // Экранирование HTML-спецсимволов: предотвращает XSS-инъекцию в письме
    // (например, если имя пользователя содержит < или &)
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
