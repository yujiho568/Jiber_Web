package com.jiber.backend.favorite;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {

    public FavoriteApartmentListResponse listFavoriteApartments() {
        return new FavoriteApartmentListResponse(List.of());
    }

    public FavoriteApartmentCreateResponse addFavoriteApartment(FavoriteApartmentCreateRequest request) {
        return new FavoriteApartmentCreateResponse(
                0L,
                request.propertyId(),
                OffsetDateTime.now(),
                "관심 아파트에 추가했습니다."
        );
    }

    public FavoriteApartmentDeleteResponse removeFavoriteApartment(Long propertyId) {
        return new FavoriteApartmentDeleteResponse(propertyId, "관심 아파트에서 삭제했습니다.");
    }

    public FavoriteAreaListResponse listFavoriteAreas() {
        return new FavoriteAreaListResponse(List.of());
    }

    public FavoriteAreaCreateResponse addFavoriteArea(FavoriteAreaCreateRequest request) {
        return new FavoriteAreaCreateResponse(
                0L,
                request.label(),
                OffsetDateTime.now(),
                "관심 지역에 추가했습니다."
        );
    }

    public FavoriteAreaDeleteResponse removeFavoriteArea(Long favoriteAreaId) {
        return new FavoriteAreaDeleteResponse(favoriteAreaId, "관심 지역에서 삭제했습니다.");
    }
}
