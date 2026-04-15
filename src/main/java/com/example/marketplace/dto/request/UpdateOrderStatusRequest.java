package com.example.marketplace.dto.request;

import com.example.marketplace.entity.enums.OrderStatus;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {
    private OrderStatus status;
}
