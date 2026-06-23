package com.jiber.backend.favorite;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.property.LatestTransactionResponse;
import com.jiber.backend.property.PropertyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        return new FavoriteAreaListResponse(
                favoriteMapper.findFavoriteAreas(userId).stream()
                        .map(this::toAreaItem)
                        .toList()
        );
    }

    public FavoriteAreaCreateResponse addFavoriteArea(Long userId, FavoriteAreaCreateRequest request) {
        requireUserId(userId);
        var command = toAreaInsertCommand(userId, request);
        if (favoriteMapper.existsFavoriteAreaByNormalizedKey(userId, command.normalizedKey())) {
            throw new ApiException(ErrorCode.FAVORITE_AREA_ALREADY_EXISTS);
        }

        var inserted = favoriteMapper.insertFavoriteArea(command);
        if (inserted == 0) {
            throw new ApiException(ErrorCode.FAVORITE_AREA_ALREADY_EXISTS);
        }
        var row = favoriteMapper.findFavoriteAreaByNormalizedKey(userId, command.normalizedKey())
                .orElseThrow(() -> new ApiException(ErrorCode.FAVORITE_AREA_NOT_FOUND));

        return new FavoriteAreaCreateResponse(
                row.getFavoriteAreaId(),
                row.getLabel(),
                row.getCreatedAt(),
                "관심 지역에 추가했습니다."
        );
    }

    public FavoriteAreaDeleteResponse removeFavoriteArea(Long userId, Long favoriteAreaId) {
        requireUserId(userId);
        var deleted = favoriteMapper.deleteFavoriteArea(userId, favoriteAreaId);
        if (deleted == 0) {
            throw new ApiException(ErrorCode.FAVORITE_AREA_NOT_FOUND);
        }
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

    private FavoriteAreaItemResponse toAreaItem(FavoriteAreaRow row) {
        return new FavoriteAreaItemResponse(
                row.getFavoriteAreaId(),
                row.getLabel(),
                row.getSido(),
                row.getSigungu(),
                row.getLegalDong(),
                row.getCenterLat(),
                row.getCenterLng(),
                row.getZoomLevel(),
                row.getCreatedAt()
        );
    }

    private FavoriteAreaInsertCommand toAreaInsertCommand(Long userId, FavoriteAreaCreateRequest request) {
        var label = normalizeText(request.label());
        var sido = normalizeNullableText(request.sido());
        var sigungu = normalizeNullableText(request.sigungu());
        var legalDong = normalizeNullableText(request.legalDong());
        var centerLat = normalizeCoordinate(request.centerLat());
        var centerLng = normalizeCoordinate(request.centerLng());
        var canonicalKey = String.join("|",
                "AREA",
                safeKeyPart(sido),
                safeKeyPart(sigungu),
                safeKeyPart(legalDong),
                safeKeyPart(coordinateKey(centerLat)),
                safeKeyPart(coordinateKey(centerLng)),
                request.zoomLevel() == null ? "" : request.zoomLevel().toString()
        );
        var normalizedKey = hashAreaKey(canonicalKey);

        return new FavoriteAreaInsertCommand(
                userId,
                label,
                sido,
                sigungu,
                legalDong,
                centerLat,
                centerLng,
                request.zoomLevel(),
                normalizedKey
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

    private String normalizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeText(value);
    }

    private BigDecimal normalizeCoordinate(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(7, RoundingMode.HALF_UP);
    }

    private String coordinateKey(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String safeKeyPart(String value) {
        return value == null ? "" : value;
    }

    private String hashAreaKey(String canonicalKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalKey.getBytes(StandardCharsets.UTF_8));
            return "AREA_SHA256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }
}
