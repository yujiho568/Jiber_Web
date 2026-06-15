package com.jiber.backend.favorite;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/apartments")
    public FavoriteApartmentListResponse listFavoriteApartments() {
        return favoriteService.listFavoriteApartments();
    }

    @PostMapping("/apartments")
    public FavoriteApartmentCreateResponse addFavoriteApartment(
            @Valid @RequestBody FavoriteApartmentCreateRequest request
    ) {
        return favoriteService.addFavoriteApartment(request);
    }

    @DeleteMapping("/apartments/{propertyId}")
    public FavoriteApartmentDeleteResponse removeFavoriteApartment(@PathVariable Long propertyId) {
        return favoriteService.removeFavoriteApartment(propertyId);
    }

    @GetMapping("/areas")
    public FavoriteAreaListResponse listFavoriteAreas() {
        return favoriteService.listFavoriteAreas();
    }

    @PostMapping("/areas")
    public FavoriteAreaCreateResponse addFavoriteArea(@Valid @RequestBody FavoriteAreaCreateRequest request) {
        return favoriteService.addFavoriteArea(request);
    }

    @DeleteMapping("/areas/{favoriteAreaId}")
    public FavoriteAreaDeleteResponse removeFavoriteArea(@PathVariable Long favoriteAreaId) {
        return favoriteService.removeFavoriteArea(favoriteAreaId);
    }
}
