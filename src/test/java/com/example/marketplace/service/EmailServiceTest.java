package com.example.marketplace.service;

import com.example.marketplace.entity.EmailLog;
import com.example.marketplace.entity.Order;
import com.example.marketplace.entity.OrderItem;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.OrderStatus;
import com.example.marketplace.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Юнит-тесты для EmailService — проверяем отправку писем и запись логов.
// JavaMailSender заменён моком, поэтому реальный SMTP не вызывается.
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender     mailSender;
    @Mock EmailLogRepository emailLogRepository;

    @InjectMocks
    EmailService emailService;

    // Поле @Value не инжектируется Mockito — подставляем вручную через ReflectionTestUtils
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "noreply@marketplace.ru");
    }

    // Вспомогательный метод: создаёт MimeMessage-заглушку.
    // MimeMessage(null) — валидный объект без реальной почтовой сессии; используется в тестах.
    private MimeMessage stubMimeMessage() {
        MimeMessage msg = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(msg);
        return msg;
    }

    // Вспомогательный метод: создаёт заказ с одной позицией для тестирования чека
    private Order makeOrderWithItem() {
        User seller = new User();
        seller.setId(3L);

        com.example.marketplace.entity.Product product = new com.example.marketplace.entity.Product();
        product.setId(1L);
        product.setName("Ноутбук Dell");
        product.setPrice(new BigDecimal("89999.99"));
        product.setSeller(seller);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtOrder(new BigDecimal("89999.99"));

        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("89999.99"));
        order.setShippingAddress("Москва, ул. Тестовая, 1");
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    // Вспомогательный метод: создаёт пользователя-покупателя
    private User makeUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("buyer@example.com");
        u.setFullName("Иван Покупатель");
        return u;
    }

    // ── sendOrderReceipt ───────────────────────────────────────────────────────

    @Test
    void sendOrderReceipt_callsMailSenderSend() {
        // Успешная отправка чека: mailSender.send() должен быть вызван ровно один раз
        stubMimeMessage();

        emailService.sendOrderReceipt(makeUser(), makeOrderWithItem(), "CARD");

        // verify(..., times(1)) — проверяем что send() был вызван ровно один раз
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendOrderReceipt_success_savesSuccessLog() {
        // После успешной отправки в emailLogRepository должна появиться запись с success=true
        stubMimeMessage();
        // thenAnswer(inv -> inv.getArgument(0)) — возвращаем переданный объект без изменений
        when(emailLogRepository.save(any(EmailLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        emailService.sendOrderReceipt(makeUser(), makeOrderWithItem(), "CARD");

        // ArgumentCaptor позволяет перехватить аргумент, переданный в save(), и проверить его
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();

        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getRecipient()).isEqualTo("buyer@example.com");
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void sendOrderReceipt_smtpFails_doesNotThrowException() {
        // Сбой SMTP не должен отменять оплату — метод обязан поглотить исключение
        stubMimeMessage();
        // MailSendException — исключение Spring Mail при сбое отправки
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        // assertThatCode — проверяем что блок кода выполняется БЕЗ исключения
        org.assertj.core.api.Assertions.assertThatCode(
                () -> emailService.sendOrderReceipt(makeUser(), makeOrderWithItem(), "CARD")
        ).doesNotThrowAnyException();
    }

    @Test
    void sendOrderReceipt_smtpFails_savesFailureLog() {
        // При сбое отправки в лог должна попасть запись с success=false и текстом ошибки
        stubMimeMessage();
        doThrow(new MailSendException("SMTP timeout"))
                .when(mailSender).send(any(MimeMessage.class));
        when(emailLogRepository.save(any(EmailLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        emailService.sendOrderReceipt(makeUser(), makeOrderWithItem(), "CARD");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();

        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getErrorMessage()).contains("SMTP timeout");
    }

    // ── sendCustomEmail ────────────────────────────────────────────────────────

    @Test
    void sendCustomEmail_callsMailSenderSend() {
        // Успешная отправка произвольного письма администратором
        stubMimeMessage();

        emailService.sendCustomEmail("user@example.com", "Тема", "Текст письма");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendCustomEmail_success_savesSuccessLog() {
        stubMimeMessage();
        when(emailLogRepository.save(any(EmailLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        emailService.sendCustomEmail("user@example.com", "Тема письма", "Содержимое");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getRecipient()).isEqualTo("user@example.com");
    }

    @Test
    void sendCustomEmail_smtpFails_throwsRuntimeException() {
        // В отличие от sendOrderReceipt, здесь исключение должно пробрасываться наружу:
        // администратор должен видеть ошибку, а не получать тихий сбой
        stubMimeMessage();
        doThrow(new MailSendException("Authentication failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(
                () -> emailService.sendCustomEmail("user@example.com", "Тема", "Текст")
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ошибка отправки письма");
    }

    @Test
    void sendCustomEmail_smtpFails_savesFailureLog() {
        // Даже при сбое и выбросе исключения лог должен быть сохранён
        stubMimeMessage();
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));
        when(emailLogRepository.save(any(EmailLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Исключение ожидается — оборачиваем в try/catch чтобы дойти до verify
        try {
            emailService.sendCustomEmail("user@example.com", "Тема", "Текст");
        } catch (RuntimeException ignored) {}

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
    }
}
