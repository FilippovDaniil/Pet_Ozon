package com.example.marketplace.payment;

import com.example.marketplace.config.AlfaBankProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты HTTP-клиента шлюза Альфа Банка.
 *
 * Поднимаем локальный HTTP-сервер (com.sun.net.httpserver, без внешних зависимостей)
 * и перехватываем реальный запрос, который формирует AlfaBankGatewayClient.
 *
 * Главная проверка: paymentOrderBinding.do отправляет tii=U (Merchant Initiated Transaction)
 * — это и делает рекуррентное списание тихим (без CVC и 3DS).
 */
class AlfaBankGatewayClientTest {

    private HttpServer server;
    private AlfaBankGatewayClient gateway;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile String responseJson = "{\"errorCode\":\"0\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            byte[] req = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(req, StandardCharsets.UTF_8));
            byte[] resp = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        AlfaBankProperties props = new AlfaBankProperties();
        props.setGatewayUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        props.setUserName("test-api");
        props.setPassword("test-pass");

        gateway = new AlfaBankGatewayClient(props, new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ── tii=U: тихий MIT ───────────────────────────────────────────────────────

    // Ключевой тест: рекуррентное списание уходит с tii=U и без CVC.
    @Test
    void paymentOrderBinding_sendsTiiU_andNoCvc() {
        responseJson = "{\"errorCode\":0,\"orderId\":\"pay-1\"}";

        gateway.paymentOrderBinding("md-1", 10000L, "binding-1");

        String body = lastBody.get();
        assertThat(lastPath.get()).isEqualTo("/paymentOrderBinding.do");
        assertThat(body).contains("tii=U");            // MIT → списание без CVC и 3DS
        assertThat(body).contains("mdOrder=md-1");
        assertThat(body).contains("bindingId=binding-1");
        assertThat(body).contains("amount=10000");
        assertThat(body).contains("ip=127.0.0.1");
        assertThat(body).doesNotContain("cvc");        // CVC не передаётся при тихом рекурренте
        assertThat(body).contains("userName=test-api");
    }

    // JSON-ответ шлюза корректно разбирается в JsonNode.
    @Test
    void paymentOrderBinding_returnsParsedResponse() {
        responseJson = "{\"errorCode\":0,\"orderId\":\"pay-77\",\"info\":\"Ваш платёж обработан\"}";

        JsonNode res = gateway.paymentOrderBinding("md-77", 5000L, "b-77");

        assertThat(res.path("orderId").asText()).isEqualTo("pay-77");
    }

    // ── обработка errorCode ──────────────────────────────────────────────────────

    // Отсутствие errorCode = успех (дефолт "0"), исключения быть не должно.
    @Test
    void call_missingErrorCode_treatedAsSuccess() {
        // Альфа Банк не включает errorCode при успехе → дефолт "0", без исключения
        responseJson = "{\"orderId\":\"x\",\"formUrl\":\"http://f\"}";

        JsonNode res = gateway.registerOrder("ord-1", 10000L, "http://r", "http://f");

        assertThat(res.path("orderId").asText()).isEqualTo("x");
    }

    // Ненулевой errorCode → RuntimeException с текстом ошибки из errorMessage.
    @Test
    void call_nonZeroErrorCode_throwsWithMessage() {
        responseJson = "{\"errorCode\":\"2\",\"errorMessage\":\"Связка не найдена\"}";

        assertThatThrownBy(() -> gateway.paymentOrderBinding("md-x", 10000L, "bad-binding"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Связка не найдена");
    }

    // register.do с привязкой обязан передавать clientId.
    @Test
    void registerOrderForBinding_sendsClientId() {
        responseJson = "{\"errorCode\":0,\"orderId\":\"md-9\",\"formUrl\":\"http://f\"}";

        gateway.registerOrderForBinding("ord-9", 10000L, "http://r", "http://f", "user-1");

        String body = lastBody.get();
        assertThat(lastPath.get()).isEqualTo("/register.do");
        assertThat(body).contains("clientId=user-1");   // обязателен для создания/поиска связки
        assertThat(body).contains("orderNumber=ord-9");
    }
}
