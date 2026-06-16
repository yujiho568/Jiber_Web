package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private PropertyService service(PropertyMapper mapper, PropertyValuationClient valuationClient) {
        return new PropertyService(mapper, new PropertyAiEligibilityService(), valuationClient);
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
