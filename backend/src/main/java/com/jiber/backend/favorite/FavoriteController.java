package com.jiber.backend.favorite;

import com.jiber.backend.auth.AuthUserPrincipal;
import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public FavoriteApartmentListResponse listFavoriteApartments(
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return favoriteService.listFavoriteApartments(currentUserId(principal));
    }

    @PostMapping("/apartments")
    public FavoriteApartmentCreateResponse addFavoriteApartment(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody FavoriteApartmentCreateRequest request
    ) {
        return favoriteService.addFavoriteApartment(currentUserId(principal), request);
    }

    @DeleteMapping("/apartments/{propertyId}")
    public FavoriteApartmentDeleteResponse removeFavoriteApartment(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable Long propertyId
    ) {
        return favoriteService.removeFavoriteApartment(currentUserId(principal), propertyId);
    }

    @GetMapping("/areas")
    public FavoriteAreaListResponse listFavoriteAreas(
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return favoriteService.listFavoriteAreas(currentUserId(principal));
    }

    @PostMapping("/areas")
    public FavoriteAreaCreateResponse addFavoriteArea(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody FavoriteAreaCreateRequest request
    ) {
        return favoriteService.addFavoriteArea(currentUserId(principal), request);
    }

    @DeleteMapping("/areas/{favoriteAreaId}")
    public FavoriteAreaDeleteResponse removeFavoriteArea(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable Long favoriteAreaId
    ) {
        return favoriteService.removeFavoriteArea(currentUserId(principal), favoriteAreaId);
    }

    private Long currentUserId(AuthUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return principal.userId();
    }
}
