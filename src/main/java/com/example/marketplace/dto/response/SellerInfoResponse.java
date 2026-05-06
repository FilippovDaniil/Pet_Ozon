package com.example.marketplace.dto.response;

import lombok.Data;

@Data
public class SellerInfoResponse {
    private Long id;
    private String fullName;
    private String shopName;
    private String email;
}
