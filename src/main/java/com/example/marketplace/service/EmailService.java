package com.example.marketplace.service;

import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.OrderItem;
import com.example.marketplace.entity.User;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /** HTML-чек покупателю после успешной оплаты. */
    public void sendOrderReceipt(User user, Order order, String paymentMethod) {
        String html = buildReceiptHtml(user, order, paymentMethod);
        sendHtml(user.getEmail(), "Чек об оплате — Заказ #" + order.getId() + " · Marketplace", html);
    }

    /** Произвольное письмо от администратора. */
    public void sendCustomEmail(String to, String subject, String text) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, false);
            mailSender.send(msg);
            log.info("ACTION=EMAIL_SENT to={} subject=\"{}\"", to, subject);
        } catch (MailException | MessagingException e) {
            log.error("Email send failed to={}: {}", to, e.getMessage());
            throw new RuntimeException("Ошибка отправки письма: " + e.getMessage(), e);
        }
    }

    // ── private ────────────────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
            log.info("ACTION=RECEIPT_SENT to={}", to);
        } catch (MailException | MessagingException e) {
            // Email failure must NOT roll back payment — just log
            log.warn("Receipt email failed for to={}: {}", to, e.getMessage());
        }
    }

    private String buildReceiptHtml(User user, Order order, String paymentMethod) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", new Locale("ru"));
        String paidAt = LocalDateTime.now().format(dtf);

        String customerName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() : user.getEmail();

        String methodLabel = switch (paymentMethod != null ? paymentMethod.toUpperCase() : "") {
            case "CARD"     -> "&#128179; Банковская карта";
            case "CASH"     -> "&#128181; Наличные";
            case "CRYPTO"   -> "&#8383; Криптовалюта";
            case "TRANSFER" -> "&#127974; Банковский перевод";
            default -> paymentMethod != null ? paymentMethod : "Карта";
        };

        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            String name = item.getProduct() != null ? item.getProduct().getName() : "Товар";
            BigDecimal sub = item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()));
            rows.append("<tr>")
                .append(td(name, "left"))
                .append(td(String.valueOf(item.getQuantity()), "center"))
                .append(td(fmt.format(item.getPriceAtOrder()), "right"))
                .append(td("<b>" + fmt.format(sub) + "</b>", "right"))
                .append("</tr>");
        }

        String address = order.getShippingAddress() != null ? order.getShippingAddress() : "Не указан";

        return "<!DOCTYPE html><html lang='ru'><head><meta charset='UTF-8'></head>"
            + "<body style='font-family:Arial,sans-serif;background:#f3f4f6;margin:0;padding:24px'>"
            + "<div style='max-width:600px;margin:0 auto;background:white;border-radius:12px;"
            +             "overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1)'>"

            // ── Шапка
            + "<div style='background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:32px;text-align:center;color:white'>"
            + "<div style='font-size:44px;line-height:1.1;margin-bottom:10px'>&#128717;</div>"
            + "<h1 style='margin:0;font-size:26px;font-weight:800'>Marketplace</h1>"
            + "<p style='margin:8px 0 0;opacity:.85;font-size:15px'>Спасибо за покупку, " + esc(customerName) + "!</p>"
            + "</div>"

            // ── Детали
            + "<div style='padding:28px'>"
            + "<table style='width:100%;border-collapse:collapse;background:#f8fafc;border-radius:10px;margin-bottom:24px'>"
            + "<tbody>"
            + infoRow("Номер заказа", "#" + order.getId())
            + infoRow("Дата оплаты", paidAt)
            + infoRow("Способ оплаты", methodLabel)
            + infoRow("Адрес доставки", esc(address))
            + "</tbody></table>"

            // ── Состав заказа
            + "<h3 style='color:#374151;margin:0 0 12px;font-size:16px;font-weight:700'>Состав заказа</h3>"
            + "<table style='width:100%;border-collapse:collapse;margin-bottom:20px'>"
            + "<thead><tr style='background:#f1f5f9'>"
            + th("Товар", "left") + th("Кол-во", "center") + th("Цена", "right") + th("Сумма", "right")
            + "</tr></thead>"
            + "<tbody>" + rows + "</tbody>"
            + "<tfoot><tr style='background:#f0fdf4'>"
            + "<td colspan='3' style='padding:14px 12px;font-size:15px;font-weight:700;color:#15803d'>Итого к оплате</td>"
            + "<td style='padding:14px 12px;font-size:17px;font-weight:800;color:#15803d;text-align:right'>"
            + fmt.format(order.getTotalAmount()) + "</td>"
            + "</tr></tfoot></table>"
            + "</div>"

            // ── Подвал
            + "<div style='background:#f8fafc;padding:18px;text-align:center;color:#9ca3af;"
            +             "font-size:12px;border-top:1px solid #e5e7eb'>"
            + "<p style='margin:0 0 4px'>Marketplace &middot; Spring Boot 3.4.4 &middot; Pet-проект</p>"
            + "<p style='margin:0'>По вопросам обращайтесь в службу поддержки</p>"
            + "</div>"
            + "</div></body></html>";
    }

    private String infoRow(String label, String value) {
        return "<tr>"
            + "<td style='padding:8px 14px;font-size:14px;color:#6b7280;border-bottom:1px solid #e5e7eb'>" + label + "</td>"
            + "<td style='padding:8px 14px;font-size:14px;font-weight:600;color:#111827;text-align:right;border-bottom:1px solid #e5e7eb'>" + value + "</td>"
            + "</tr>";
    }

    private String th(String text, String align) {
        return "<th style='text-align:" + align + ";padding:10px 12px;font-size:11px;color:#6b7280;"
            + "text-transform:uppercase;letter-spacing:.5px'>" + text + "</th>";
    }

    private String td(String text, String align) {
        return "<td style='padding:11px 12px;border-bottom:1px solid #f1f5f9;font-size:14px;text-align:" + align + "'>" + text + "</td>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
