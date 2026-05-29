package com.example.marketplace.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logback-аппендер для отправки логов приложения в OpenSearch.
 *
 * Работает через _bulk API (ndjson). Батчит события и отправляет раз в
 * flushIntervalSeconds секунд или при накоплении batchSize событий.
 *
 * event.prepareForDeferredProcessing() вызывается сразу при append() —
 * это замораживает MDC-поля (requestId, userId, role) до момента отправки,
 * потому что MDC — ThreadLocal и очищается по завершении запроса.
 *
 * Индекс создаётся по шаблону: {indexPrefix}-YYYY-MM-DD (ежедневная ротация).
 * Discover в OpenSearch Dashboards может фильтровать по дате.
 *
 * Конфигурируется из logback-spring.xml через сеттеры:
 *   <host>, <port>, <indexPrefix>, <batchSize>, <flushIntervalSeconds>
 */
public class OpenSearchLogAppender extends AppenderBase<ILoggingEvent> {

    // Настраиваются через logback XML
    private String host               = "localhost";
    private int    port               = 9200;
    private String indexPrefix        = "marketplace-logs";
    private int    batchSize          = 50;
    private int    flushIntervalSecs  = 3;

    private final List<String>         buffer    = new ArrayList<>();
    private       ScheduledExecutorService scheduler;
    private final HttpClient           httpClient;

    public OpenSearchLogAppender() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void start() {
        super.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "os-log-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalSecs, flushIntervalSecs, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        flush();
        if (scheduler != null) scheduler.shutdown();
        super.stop();
    }

    @Override
    protected synchronized void append(ILoggingEvent event) {
        // Замораживаем MDC и форматированное сообщение ДО очистки ThreadLocal
        event.prepareForDeferredProcessing();
        buffer.add(toBulkEntry(event));
        if (buffer.size() >= batchSize) flush();
    }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;
        List<String> batch = new ArrayList<>(buffer);
        buffer.clear();

        StringBuilder body = new StringBuilder();
        for (String entry : batch) {
            body.append(entry).append('\n');
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/_bulk"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                addError("OpenSearch bulk error " + res.statusCode() + ": " + res.body().substring(0, Math.min(200, res.body().length())));
            }
        } catch (Exception e) {
            // Graceful degradation: OpenSearch упал — не роняем приложение
            addError("Cannot reach OpenSearch at " + host + ":" + port + " — " + e.getMessage());
        }
    }

    // Формирует одну запись _bulk: строка action + строка document
    private String toBulkEntry(ILoggingEvent event) {
        String index = indexPrefix + "-" + LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        return "{\"index\":{\"_index\":\"" + index + "\"}}\n" + buildDoc(event);
    }

    private String buildDoc(ILoggingEvent event) {
        String timestamp = Instant.ofEpochMilli(event.getTimeStamp())
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, String> mdc = event.getMDCPropertyMap();

        return "{" +
                field("@timestamp",  timestamp,                                     true)  +
                field("level",       event.getLevel().levelStr,                     false) +
                field("logger",      shorten(event.getLoggerName()),                false) +
                field("thread",      event.getThreadName(),                         false) +
                field("message",     event.getFormattedMessage(),                   false) +
                field("request_id",  mdc.getOrDefault("requestId",  ""),           false) +
                field("user_id",     mdc.getOrDefault("userId",     ""),           false) +
                field("user_email",  mdc.getOrDefault("userEmail",  ""),           false) +
                field("role",        mdc.getOrDefault("role",       "system"),     false) +
                "}";
    }

    private String field(String key, String value, boolean first) {
        return (first ? "" : ",") + "\"" + key + "\":\"" + esc(value) + "\"";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // "com.example.marketplace.service.ProductService" → "service.ProductService"
    private String shorten(String name) {
        if (name == null) return "";
        String[] p = name.split("\\.");
        return p.length >= 2 ? p[p.length - 2] + "." + p[p.length - 1] : name;
    }

    // Сеттеры для logback XML
    public void setHost(String host)                         { this.host = host; }
    public void setPort(int port)                            { this.port = port; }
    public void setIndexPrefix(String indexPrefix)           { this.indexPrefix = indexPrefix; }
    public void setBatchSize(int batchSize)                  { this.batchSize = batchSize; }
    public void setFlushIntervalSecs(int flushIntervalSecs)  { this.flushIntervalSecs = flushIntervalSecs; }
}
