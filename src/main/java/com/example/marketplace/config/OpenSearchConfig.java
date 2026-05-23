package com.example.marketplace.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Бин OpenSearchClient для инъекции в ProductSearchService.
// Транспорт: Apache HttpClient 5.x — современный стандарт, совместим со Spring Boot 3.x.
// Для local/docker/k8s используем HTTP без TLS (DISABLE_SECURITY_PLUGIN=true в OpenSearch).
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        // HttpHost(scheme, hostname, port) — порядок аргументов в httpclient5 (в отличие от 4.x).
        HttpHost httpHost = new HttpHost(scheme, host, port);
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())
                .build();
        return new OpenSearchClient(transport);
    }
}
