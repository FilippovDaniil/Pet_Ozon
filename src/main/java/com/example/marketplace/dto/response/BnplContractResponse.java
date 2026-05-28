package com.example.marketplace.dto.response;

import java.util.List;

public record BnplContractResponse(
        Long                          id,
        Long                          orderId,
        String                        product,
        String                        productDescription,
        Long                          totalAmountKopecks,
        Long                          commissionKopecks,
        Integer                       installmentCount,
        String                        status,
        Long                          depositedAmountKopecks,
        List<BnplInstallmentResponse> installments
) {}
