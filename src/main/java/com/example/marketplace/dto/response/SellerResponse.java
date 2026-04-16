package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SellerResponse {
    private Long id;
    private String email;
    private String fullName;
    private String shopName;
    private BigDecimal balance;
}
