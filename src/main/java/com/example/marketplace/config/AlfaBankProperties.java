package com.example.marketplace.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Параметры подключения к шлюзу Альфа Банка.
 * Значения берутся из application.properties (префикс alfabank).
 */
@Component
@ConfigurationProperties(prefix = "alfabank")
@Getter
@Setter
public class AlfaBankProperties {

    private String gatewayUrl  = "https://alfa.rbsuat.com/payment/rest/";
    private String userName;
    private String password;
    private String returnUrl;
    private String failUrl;
}
