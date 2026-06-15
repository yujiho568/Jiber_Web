package com.jiber.backend.property;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping("/map")
    public PropertyMapResponse mapSearch(@Valid @ParameterObject @ModelAttribute MapSearchRequest request) {
        request.validateRanges();
        return propertyService.findMapProperties(request);
    }

    @GetMapping("/search")
    public PropertySearchResponse filterSearch(@Valid @ParameterObject @ModelAttribute PropertySearchRequest request) {
        request.validateRanges();
        return propertyService.searchProperties(request);
    }

    @GetMapping("/{propertyId}")
    public PropertyDetailResponse detail(@PathVariable Long propertyId) {
        return propertyService.getPropertyDetail(propertyId);
    }

    @PostMapping("/{propertyId}/valuation")
    public ValuationResponse valuation(
            @PathVariable Long propertyId,
            @Valid @RequestBody ValuationRequest request
    ) {
        return propertyService.valuateApartment(propertyId, request);
    }

    @PostMapping("/{propertyId}/shap")
    public ShapResponse shap(
            @PathVariable Long propertyId,
            @Valid @RequestBody ShapRequest request
    ) {
        return propertyService.explainApartment(propertyId, request);
    }
}
