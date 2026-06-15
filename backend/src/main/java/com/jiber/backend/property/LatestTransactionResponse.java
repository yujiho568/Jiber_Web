package com.jiber.backend.property;

import java.time.LocalDate;

public record LatestTransactionResponse(
        TransactionType transactionType,
        Long dealAmount,
        LocalDate dealDate
) {
}
