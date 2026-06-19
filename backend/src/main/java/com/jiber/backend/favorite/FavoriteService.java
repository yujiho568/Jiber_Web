package com.jiber.backend.favorite;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.property.LatestTransactionResponse;
import com.jiber.backend.property.PropertyType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public FavoriteApartmentListResponse listFavoriteApartments(Long userId) {
        requireUserId(userId);
        return new FavoriteApartmentListResponse(
                favoriteMapper.findFavoriteApartments(userId).stream()
                        .map(this::toApartmentItem)
                        .toList()
        );
    }

    public FavoriteApartmentCreateResponse addFavoriteApartment(Long userId, FavoriteApartmentCreateRequest request) {
        requireUserId(userId);
        ensureApartmentProperty(request.propertyId());
        if (favoriteMapper.existsFavoriteApartment(userId, request.propertyId())) {
            throw new ApiException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        var inserted = favoriteMapper.insertFavoriteApartment(userId, request.propertyId());
        if (inserted == 0) {
            throw new ApiException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        var row = favoriteMapper.findFavoriteApartment(userId, request.propertyId())
                .orElseThrow(() -> new ApiException(ErrorCode.FAVORITE_NOT_FOUND));

        return new FavoriteApartmentCreateResponse(
                row.getFavoriteId(),
                row.getPropertyId(),
                row.getCreatedAt(),
                "관심 아파트에 추가했습니다."
        );
    }

    public FavoriteApartmentDeleteResponse removeFavoriteApartment(Long userId, Long propertyId) {
        requireUserId(userId);
        ensureApartmentProperty(propertyId);
        var deleted = favoriteMapper.deleteFavoriteApartment(userId, propertyId);
        if (deleted == 0) {
            throw new ApiException(ErrorCode.FAVORITE_NOT_FOUND);
        }
        return new FavoriteApartmentDeleteResponse(propertyId, "관심 아파트에서 삭제했습니다.");
    }

    public FavoriteAreaListResponse listFavoriteAreas(Long userId) {
        requireUserId(userId);
        return new FavoriteAreaListResponse(List.of());
    }

    public FavoriteAreaCreateResponse addFavoriteArea(Long userId, FavoriteAreaCreateRequest request) {
        requireUserId(userId);
        return new FavoriteAreaCreateResponse(
                0L,
                request.label(),
                OffsetDateTime.now(),
                "관심 지역에 추가했습니다."
        );
    }

    public FavoriteAreaDeleteResponse removeFavoriteArea(Long userId, Long favoriteAreaId) {
        requireUserId(userId);
        return new FavoriteAreaDeleteResponse(favoriteAreaId, "관심 지역에서 삭제했습니다.");
    }

    private FavoriteApartmentItemResponse toApartmentItem(FavoriteApartmentRow row) {
        return new FavoriteApartmentItemResponse(
                row.getFavoriteId(),
                row.getPropertyId(),
                row.getPropertyType(),
                row.getName(),
                row.getAddress(),
                toDouble(row.getLatitude()),
                toDouble(row.getLongitude()),
                toLatestTransaction(row),
                row.getCreatedAt()
        );
    }

    private LatestTransactionResponse toLatestTransaction(FavoriteApartmentRow row) {
        if (row.getLatestTransactionType() == null) {
            return null;
        }
        return new LatestTransactionResponse(
                row.getLatestTransactionType(),
                row.getLatestDealAmount(),
                row.getLatestDealDate()
        );
    }

    private void ensureApartmentProperty(Long propertyId) {
        var propertyType = favoriteMapper.findPropertyTypeById(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROPERTY_NOT_FOUND));
        if (propertyType != PropertyType.APARTMENT) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "관심 아파트에는 아파트만 추가할 수 있습니다.");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
