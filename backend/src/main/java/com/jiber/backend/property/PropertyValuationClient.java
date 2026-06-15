package com.jiber.backend.property;

public interface PropertyValuationClient {

    ValuationResponse valuateApartment(Long propertyId, ValuationRequest request);

    ShapResponse explainApartment(Long propertyId, ShapRequest request);
}
