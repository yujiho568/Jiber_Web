package com.jiber.backend.property;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class PropertyAiEligibilityService {

    public PropertyType ensureApartmentSupported(PropertyType propertyType) {
        if (propertyType != PropertyType.APARTMENT) {
            throw new ApiException(ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE);
        }
        return propertyType;
    }
}
