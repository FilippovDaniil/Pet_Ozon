package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AccountantSummaryResponse {
    private long totalOrders;
    private long paidOrders;
    private BigDecimal totalRevenue;
    private long totalClients;
    private long cartItemsCount;
    private BigDecimal potentialRevenue;
    private long emailsSent;
    private long emailsSuccess;
}
