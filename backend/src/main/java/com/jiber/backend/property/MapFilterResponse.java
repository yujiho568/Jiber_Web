package com.jiber.backend.property;

import java.util.List;

public record MapFilterResponse(
        List<PropertyType> propertyTypes,
        List<TransactionType> transactionTypes,
        Integer zoomLevel
) {
}
