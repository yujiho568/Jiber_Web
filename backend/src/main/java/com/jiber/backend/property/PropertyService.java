package com.jiber.backend.property;

import com.jiber.backend.auth.AuthUserPrincipal;
import com.jiber.backend.common.PageMetadata;
import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.favorite.FavoriteMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PropertyService {

    private static final int DETAIL_TRANSACTION_LIMIT = 20;

    private final PropertyMapper propertyMapper;
    private final FavoriteMapper favoriteMapper;
    private final PropertyAiEligibilityService eligibilityService;
    private final PropertyValuationClient valuationClient;
    private final Clock clock;

    @Autowired
    public PropertyService(
            PropertyMapper propertyMapper,
            FavoriteMapper favoriteMapper,
            PropertyAiEligibilityService eligibilityService,
            PropertyValuationClient valuationClient
    ) {
        this(propertyMapper, favoriteMapper, eligibilityService, valuationClient, Clock.systemDefaultZone());
    }

    PropertyService(
            PropertyMapper propertyMapper,
            FavoriteMapper favoriteMapper,
            PropertyAiEligibilityService eligibilityService,
            PropertyValuationClient valuationClient,
            Clock clock
    ) {
        this.propertyMapper = propertyMapper;
        this.favoriteMapper = favoriteMapper;
        this.eligibilityService = eligibilityService;
        this.valuationClient = valuationClient;
        this.clock = clock;
    }

    public PropertyMapResponse findMapProperties(MapSearchRequest request) {
        var recentSince = LocalDate.now(clock).minusMonths(6);
        return new PropertyMapResponse(
                propertyMapper.findMapProperties(request, recentSince).stream()
                        .map(this::toMapItem)
                        .toList(),
                administrativeClusters(request, recentSince),
                new BoundsResponse(request.swLat(), request.swLng(), request.neLat(), request.neLng()),
                new MapFilterResponse(request.propertyTypes(), request.transactionTypes(), request.zoomLevel())
        );
    }

    public PropertySearchResponse searchProperties(PropertySearchRequest request) {
        var page = request.effectivePage();
        var size = request.effectiveSize();
        var offset = page * size;
        var items = propertyMapper.searchProperties(request, size, offset).stream()
                .map(this::toSearchItem)
                .toList();
        var totalElements = propertyMapper.countSearchProperties(request);
        return new PropertySearchResponse(items, pageMetadata(page, size, totalElements));
    }

    public PropertyDetailResponse getPropertyDetail(Long propertyId) {
        return getPropertyDetail(propertyId, null);
    }

    public PropertyDetailResponse getPropertyDetail(Long propertyId, AuthUserPrincipal principal) {
        var row = propertyMapper.findDetailById(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROPERTY_NOT_FOUND));
        var transactions = propertyMapper.findRecentTransactions(propertyId, DETAIL_TRANSACTION_LIMIT).stream()
                .map(this::toTransaction)
                .toList();
        var aiAvailable = row.getPropertyType() == PropertyType.APARTMENT;
        var apartmentFavorited = principal != null
                && principal.userId() != null
                && favoriteMapper.existsFavoriteApartment(principal.userId(), propertyId);
        return new PropertyDetailResponse(
                row.getPropertyId(),
                row.getPropertyType(),
                row.getName(),
                new PropertyDetailResponse.Address(row.getSido(), row.getSigungu(), row.getLegalDong(), row.getRoadAddress()),
                new PropertyDetailResponse.Location(toDouble(row.getLatitude()), toDouble(row.getLongitude())),
                new PropertyDetailResponse.Summary(row.getBuiltYear(), row.getHouseholdCount(), row.getLatestDealAmount(), row.getLatestDealDate()),
                transactions,
                new PropertyDetailResponse.FavoriteSummary(apartmentFavorited, false),
                new PropertyDetailResponse.AiMetadata(
                        aiAvailable,
                        aiAvailable,
                        aiAvailable ? null : ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE.name()
                )
        );
    }

    public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
        var propertyType = resolvePropertyTypeForAi(propertyId);
        eligibilityService.ensureApartmentSupported(propertyType);
        return valuationClient.valuateApartment(propertyId, request);
    }

    public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
        var propertyType = resolvePropertyTypeForAi(propertyId);
        eligibilityService.ensureApartmentSupported(propertyType);
        return valuationClient.explainApartment(propertyId, request);
    }

    private PropertyType resolvePropertyTypeForAi(Long propertyId) {
        return propertyMapper.findPropertyTypeById(propertyId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROPERTY_NOT_FOUND));
    }

    private PropertyMapItemResponse toMapItem(PropertyListRow row) {
        return new PropertyMapItemResponse(
                row.getPropertyId(),
                row.getPropertyType(),
                row.getName(),
                row.getAddress(),
                toDouble(row.getLatitude()),
                toDouble(row.getLongitude()),
                toLatestTransaction(row),
                row.getDealCount() == null ? 0 : row.getDealCount(),
                row.getRecentTransactionCount(),
                row.getPropertyType() == PropertyType.APARTMENT
        );
    }

    private List<AdministrativeClusterResponse> administrativeClusters(MapSearchRequest request, LocalDate recentSince) {
        if (request.zoomLevel() < 5) {
            return List.of();
        }
        if (request.zoomLevel() < 7) {
            return propertyMapper.findLegalDongClusters(request, recentSince).stream()
                    .map(row -> toAdministrativeCluster(row, AdministrativeClusterLevel.LEGAL_DONG))
                    .toList();
        }
        return propertyMapper.findSigunguClusters(request, recentSince).stream()
                .map(row -> toAdministrativeCluster(row, AdministrativeClusterLevel.SIGUNGU))
                .toList();
    }

    private AdministrativeClusterResponse toAdministrativeCluster(
            AdministrativeClusterRow row,
            AdministrativeClusterLevel level
    ) {
        return new AdministrativeClusterResponse(
                clusterId(row, level),
                level,
                row.getSido(),
                row.getSigungu(),
                row.getLegalDong(),
                row.getLabel(),
                toDouble(row.getCenterLat()),
                toDouble(row.getCenterLng()),
                row.getPropertyCount() == null ? 0 : row.getPropertyCount(),
                row.getTransactionCount() == null ? 0 : row.getTransactionCount(),
                row.getAverageDealAmount()
        );
    }

    private String clusterId(AdministrativeClusterRow row, AdministrativeClusterLevel level) {
        if (level == AdministrativeClusterLevel.SIGUNGU) {
            return "SIGUNGU:%s:%s".formatted(row.getSido(), row.getSigungu());
        }
        return "LEGAL_DONG:%s:%s:%s".formatted(row.getSido(), row.getSigungu(), row.getLegalDong());
    }

    private PropertySearchItemResponse toSearchItem(PropertyListRow row) {
        return new PropertySearchItemResponse(
                row.getPropertyId(),
                row.getPropertyType(),
                row.getName(),
                row.getAddress(),
                row.getLegalDong(),
                toDouble(row.getLatitude()),
                toDouble(row.getLongitude()),
                row.getDistanceM(),
                toLatestTransaction(row),
                row.getPropertyType() == PropertyType.APARTMENT
        );
    }

    private LatestTransactionResponse toLatestTransaction(PropertyListRow row) {
        if (row.getLatestTransactionType() == null) {
            return null;
        }
        return new LatestTransactionResponse(row.getLatestTransactionType(), row.getLatestDealAmount(), row.getLatestDealDate());
    }

    private PropertyTransactionResponse toTransaction(PropertyTransactionRow row) {
        return new PropertyTransactionResponse(
                row.getTransactionId(),
                row.getTransactionType(),
                row.getExclusiveAreaM2(),
                row.getFloor(),
                row.getDealAmount(),
                row.getDepositAmount(),
                row.getMonthlyRent(),
                row.getDealDate()
        );
    }

    private PageMetadata pageMetadata(int page, int size, long totalElements) {
        if (totalElements == 0) {
            return PageMetadata.empty(page, size);
        }
        return new PageMetadata(page, size, totalElements, (int) Math.ceil((double) totalElements / size));
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
