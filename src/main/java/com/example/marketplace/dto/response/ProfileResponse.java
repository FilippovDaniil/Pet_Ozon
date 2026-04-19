package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String address;
    private String role;
    private String shopName;
    private BigDecimal balance;
}
