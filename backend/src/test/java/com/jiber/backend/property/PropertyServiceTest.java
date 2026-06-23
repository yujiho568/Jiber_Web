package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.auth.AuthUserPrincipal;
import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.favorite.FavoriteAreaInsertCommand;
import com.jiber.backend.favorite.FavoriteAreaRow;
import com.jiber.backend.favorite.FavoriteApartmentRow;
import com.jiber.backend.favorite.FavoriteMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertyServiceTest {

    @Test
    void searchPropertiesReturnsEmptyPageFromMapper() {
        var mapper = new FakePropertyMapper();
        var service = service(mapper, new RecordingValuationClient());
        var request = new PropertySearchRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(PropertyType.APARTMENT),
                List.of(TransactionType.SALE),
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                10,
                "latestDealDate,desc"
        );

        var response = service.searchProperties(request);

        assertThat(response.items()).isEmpty();
        assertThat(response.page().number()).isEqualTo(1);
        assertThat(response.page().size()).isEqualTo(10);
        assertThat(response.page().totalElements()).isZero();
        assertThat(response.page().totalPages()).isZero();
        assertThat(mapper.searchLimit).isEqualTo(10);
        assertThat(mapper.searchOffset).isEqualTo(10);
    }

    @Test
    void propertyDetailThrowsNotFoundWhenMapperHasNoRow() {
        var mapper = new FakePropertyMapper();
        var service = service(mapper, new RecordingValuationClient());

        assertThatThrownBy(() -> service.getPropertyDetail(404L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPERTY_NOT_FOUND));
    }

    @Test
    void aiEndpointsRejectNonApartmentFromDbBeforeCallingModelServer() {
        var mapper = new FakePropertyMapper();
        mapper.propertyType = PropertyType.OFFICETEL;
        var valuationClient = new RecordingValuationClient();
        var service = service(mapper, valuationClient);

        assertThatThrownBy(() -> service.valuateApartment(1901L, valuationRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE));
        assertThatThrownBy(() -> service.explainApartment(1901L, shapRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE));
        assertThat(valuationClient.valuationCalled).isFalse();
        assertThat(valuationClient.shapCalled).isFalse();
    }

    @Test
    void mapPropertiesConvertsMapperRowsToContractResponse() {
        var mapper = new FakePropertyMapper();
        mapper.mapRows.add(sampleListRow());
        var service = service(mapper, new RecordingValuationClient());
        var request = new MapSearchRequest(
                new BigDecimal("37.40"),
                new BigDecimal("126.90"),
                new BigDecimal("37.60"),
                new BigDecimal("127.20"),
                5,
                List.of(PropertyType.APARTMENT),
                List.of(TransactionType.SALE),
                null,
                null,
                null,
                null,
                null,
                null
        );

        var response = service.findMapProperties(request);

        assertThat(response.bounds().swLat()).isEqualByComparingTo("37.40");
        assertThat(response.filters().zoomLevel()).isEqualTo(5);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.propertyId()).isEqualTo(1001L);
            assertThat(item.propertyType()).isEqualTo(PropertyType.APARTMENT);
            assertThat(item.address()).isEqualTo("서울특별시 강남구 역삼동 12-3");
            assertThat(item.latestTransaction().transactionType()).isEqualTo(TransactionType.SALE);
            assertThat(item.latestTransaction().dealAmount()).isEqualTo(1_250_000_000L);
            assertThat(item.dealCount()).isEqualTo(2);
            assertThat(item.aiAvailable()).isTrue();
        });
    }

    @Test
    void propertyDetailReturnsCanonicalLatestSummaryAndRecentTransactions() {
        var mapper = new FakePropertyMapper();
        mapper.detailRow = sampleDetailRow();
        mapper.transactionRows.add(sampleTransactionRow(5001L, TransactionType.SALE, 1_250_000_000L, null, 0L, LocalDate.of(2026, 5, 20)));
        mapper.transactionRows.add(sampleTransactionRow(5002L, TransactionType.JEONSE, null, 780_000_000L, 0L, LocalDate.of(2026, 3, 15)));
        var service = service(mapper, new RecordingValuationClient());

        var response = service.getPropertyDetail(1001L);

        assertThat(response.propertyId()).isEqualTo(1001L);
        assertThat(response.summary().latestDealAmount()).isEqualTo(1_250_000_000L);
        assertThat(response.summary().latestDealDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(response.transactions()).hasSize(2);
        assertThat(response.transactions().get(0).transactionType()).isEqualTo(TransactionType.SALE);
        assertThat(response.transactions().get(0).dealAmount()).isEqualTo(1_250_000_000L);
        assertThat(response.transactions().get(1).transactionType()).isEqualTo(TransactionType.JEONSE);
        assertThat(response.transactions().get(1).depositAmount()).isEqualTo(780_000_000L);
        assertThat(response.favorite().apartmentFavorited()).isFalse();
        assertThat(response.ai().valuationAvailable()).isTrue();
    }

    @Test
    void propertyDetailReturnsFavoriteFlagForLoggedInUser() {
        var mapper = new FakePropertyMapper();
        mapper.detailRow = sampleDetailRow();
        var favoriteMapper = new FakeFavoriteMapper();
        favoriteMapper.favorite(7L, 1001L);
        var service = service(mapper, favoriteMapper, new RecordingValuationClient());
        var principal = new AuthUserPrincipal(7L, "user@example.com", "사용자", Set.of("USER"));

        var response = service.getPropertyDetail(1001L, principal);

        assertThat(response.favorite().apartmentFavorited()).isTrue();
        assertThat(response.favorite().areaFavorited()).isFalse();
    }

    private PropertyService service(PropertyMapper mapper, PropertyValuationClient valuationClient) {
        return service(mapper, new FakeFavoriteMapper(), valuationClient);
    }

    private PropertyService service(PropertyMapper mapper, FavoriteMapper favoriteMapper, PropertyValuationClient valuationClient) {
        return new PropertyService(mapper, favoriteMapper, new PropertyAiEligibilityService(), valuationClient);
    }

    private ValuationRequest valuationRequest() {
        return new ValuationRequest(new BigDecimal("84.95"), 15, LocalDate.of(2026, 6, 12));
    }

    private ShapRequest shapRequest() {
        return new ShapRequest(new BigDecimal("84.95"), 15, LocalDate.of(2026, 6, 12));
    }

    private PropertyListRow sampleListRow() {
        var row = new PropertyListRow();
        row.setPropertyId(1001L);
        row.setPropertyType(PropertyType.APARTMENT);
        row.setName("샘플 역삼아파트");
        row.setAddress("서울특별시 강남구 역삼동 12-3");
        row.setLegalDong("역삼동");
        row.setLatitude(new BigDecimal("37.5001000"));
        row.setLongitude(new BigDecimal("127.0364000"));
        row.setLatestTransactionType(TransactionType.SALE);
        row.setLatestDealAmount(1_250_000_000L);
        row.setLatestDealDate(LocalDate.of(2026, 5, 20));
        row.setDealCount(2);
        return row;
    }

    private PropertyDetailRow sampleDetailRow() {
        var row = new PropertyDetailRow();
        row.setPropertyId(1001L);
        row.setPropertyType(PropertyType.APARTMENT);
        row.setName("샘플 역삼아파트");
        row.setSido("서울특별시");
        row.setSigungu("강남구");
        row.setLegalDong("역삼동");
        row.setRoadAddress("서울특별시 강남구 테헤란로 123");
        row.setJibunAddress("서울특별시 강남구 역삼동 12-3");
        row.setLatitude(new BigDecimal("37.5001000"));
        row.setLongitude(new BigDecimal("127.0364000"));
        row.setBuiltYear(2010);
        row.setHouseholdCount(500);
        row.setLatestDealAmount(1_250_000_000L);
        row.setLatestDealDate(LocalDate.of(2026, 5, 20));
        return row;
    }

    private PropertyTransactionRow sampleTransactionRow(
            Long transactionId,
            TransactionType transactionType,
            Long dealAmount,
            Long depositAmount,
            Long monthlyRent,
            LocalDate dealDate
    ) {
        var row = new PropertyTransactionRow();
        row.setTransactionId(transactionId);
        row.setTransactionType(transactionType);
        row.setExclusiveAreaM2(new BigDecimal("84.9500"));
        row.setFloor(15);
        row.setDealAmount(dealAmount);
        row.setDepositAmount(depositAmount);
        row.setMonthlyRent(monthlyRent);
        row.setDealDate(dealDate);
        return row;
    }

    private static class FakePropertyMapper implements PropertyMapper {

        private final List<PropertyListRow> mapRows = new ArrayList<>();
        private final List<PropertyListRow> searchRows = new ArrayList<>();
        private final List<PropertyTransactionRow> transactionRows = new ArrayList<>();
        private PropertyType propertyType;
        private PropertyDetailRow detailRow;
        private int searchLimit;
        private int searchOffset;
        private long totalElements;

        @Override
        public List<PropertyListRow> findMapProperties(MapSearchRequest request) {
            return mapRows;
        }

        @Override
        public List<PropertyListRow> searchProperties(PropertySearchRequest request, int limit, int offset) {
            this.searchLimit = limit;
            this.searchOffset = offset;
            return searchRows;
        }

        @Override
        public long countSearchProperties(PropertySearchRequest request) {
            return totalElements;
        }

        @Override
        public Optional<PropertyDetailRow> findDetailById(Long propertyId) {
            return Optional.ofNullable(detailRow);
        }

        @Override
        public List<PropertyTransactionRow> findRecentTransactions(Long propertyId, int limit) {
            return transactionRows;
        }

        @Override
        public Optional<PropertyType> findPropertyTypeById(Long propertyId) {
            return Optional.ofNullable(propertyType);
        }
    }

    private static class FakeFavoriteMapper implements FavoriteMapper {

        private final Set<String> favorites = new HashSet<>();

        void favorite(Long userId, Long propertyId) {
            favorites.add(key(userId, propertyId));
        }

        @Override
        public Optional<PropertyType> findPropertyTypeById(Long propertyId) {
            return Optional.of(PropertyType.APARTMENT);
        }

        @Override
        public List<FavoriteApartmentRow> findFavoriteApartments(Long userId) {
            return List.of();
        }

        @Override
        public Optional<FavoriteApartmentRow> findFavoriteApartment(Long userId, Long propertyId) {
            return Optional.empty();
        }

        @Override
        public int insertFavoriteApartment(Long userId, Long propertyId) {
            return 0;
        }

        @Override
        public int deleteFavoriteApartment(Long userId, Long propertyId) {
            return 0;
        }

        @Override
        public boolean existsFavoriteApartment(Long userId, Long propertyId) {
            return favorites.contains(key(userId, propertyId));
        }

        @Override
        public List<FavoriteAreaRow> findFavoriteAreas(Long userId) {
            return List.of();
        }

        @Override
        public Optional<FavoriteAreaRow> findFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
            return Optional.empty();
        }

        @Override
        public int insertFavoriteArea(FavoriteAreaInsertCommand command) {
            return 0;
        }

        @Override
        public int deleteFavoriteArea(Long userId, Long favoriteAreaId) {
            return 0;
        }

        @Override
        public boolean existsFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
            return false;
        }

        private String key(Long userId, Long propertyId) {
            return userId + ":" + propertyId;
        }
    }

    private static class RecordingValuationClient implements PropertyValuationClient {

        private boolean valuationCalled;
        private boolean shapCalled;

        @Override
        public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
            valuationCalled = true;
            return null;
        }

        @Override
        public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
            shapCalled = true;
            return null;
        }
    }
}
