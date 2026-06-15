package com.jiber.backend.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PropertyTransactionResponse(
        Long transactionId,
        TransactionType transactionType,
        BigDecimal exclusiveAreaM2,
        Integer floor,
        Long dealAmount,
        Long depositAmount,
        Long monthlyRent,
        LocalDate dealDate
) {
}
