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

    private String gatewayUrl  = "https://alfa.rbsuat.com/payment/rest/";  // базовый URL API шлюза (UAT по умолчанию)
    private String userName;          // логин мерчанта (секрет, из env/Secret)
    private String password;          // пароль мерчанта (секрет, из env/Secret)
    private String returnUrl;         // куда банк вернёт браузер после успешной оплаты
    private String failUrl;           // куда банк вернёт браузер при отклонении
    private String cardBindReturnUrl; // returnUrl отдельно для сценария привязки карты
}
