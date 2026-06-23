package com.jiber.backend.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.property.PropertyType;
import com.jiber.backend.property.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FavoriteServiceTest {

    @Test
    void userCanAddListAndDeleteOwnFavoriteApartment() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        var created = service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1001L));
        var list = service.listFavoriteApartments(1L);
        var deleted = service.removeFavoriteApartment(1L, 1001L);

        assertThat(created.propertyId()).isEqualTo(1001L);
        assertThat(list.items()).singleElement().satisfies(item -> {
            assertThat(item.favoriteId()).isEqualTo(created.favoriteId());
            assertThat(item.propertyId()).isEqualTo(1001L);
            assertThat(item.propertyType()).isEqualTo(PropertyType.APARTMENT);
            assertThat(item.latestTransaction().transactionType()).isEqualTo(TransactionType.SALE);
        });
        assertThat(deleted.propertyId()).isEqualTo(1001L);
        assertThat(service.listFavoriteApartments(1L).items()).isEmpty();
    }

    @Test
    void duplicateAddReturnsConflictAndDoesNotCreateSecondRow() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        var first = service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1001L));

        assertThatThrownBy(() -> service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1001L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS));
        assertThat(first.favoriteId()).isNotNull();
        assertThat(service.listFavoriteApartments(1L).items()).hasSize(1);
    }

    @Test
    void addMissingPropertyThrowsPropertyNotFound() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(404L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPERTY_NOT_FOUND));
    }

    @Test
    void addNonApartmentPropertyReturnsValidationFailed() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1901L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void deleteMissingPropertyThrowsPropertyNotFound() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.removeFavoriteApartment(1L, 404L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPERTY_NOT_FOUND));
    }

    @Test
    void userCannotListOrDeleteAnotherUsersFavorite() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1001L));

        assertThat(service.listFavoriteApartments(2L).items()).isEmpty();
        assertThatThrownBy(() -> service.removeFavoriteApartment(2L, 1001L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_NOT_FOUND));
        assertThat(service.listFavoriteApartments(1L).items()).hasSize(1);
    }

    @Test
    void canonicalImportedPropertyCanBeFavoritedWhenPropertyExists() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        var created = service.addFavoriteApartment(1L, new FavoriteApartmentCreateRequest(1912L));

        assertThat(created.propertyId()).isEqualTo(1912L);
        assertThat(service.listFavoriteApartments(1L).items())
                .extracting(FavoriteApartmentItemResponse::propertyId)
                .containsExactly(1912L);
    }

    @Test
    void favoriteOperationsRequireUserId() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.listFavoriteApartments(null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void userCanAddListAndDeleteOwnFavoriteArea() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        var created = service.addFavoriteArea(1L, areaRequest());
        var list = service.listFavoriteAreas(1L);
        var deleted = service.removeFavoriteArea(1L, created.favoriteAreaId());

        assertThat(created.label()).isEqualTo("강남구 역삼동");
        assertThat(list.items()).singleElement().satisfies(item -> {
            assertThat(item.favoriteAreaId()).isEqualTo(created.favoriteAreaId());
            assertThat(item.sido()).isEqualTo("서울특별시");
            assertThat(item.sigungu()).isEqualTo("강남구");
            assertThat(item.legalDong()).isEqualTo("역삼동");
            assertThat(item.centerLat()).isEqualByComparingTo("37.5001000");
            assertThat(item.centerLng()).isEqualByComparingTo("127.0364000");
            assertThat(item.zoomLevel()).isEqualTo(5);
        });
        assertThat(deleted.favoriteAreaId()).isEqualTo(created.favoriteAreaId());
        assertThat(service.listFavoriteAreas(1L).items()).isEmpty();
    }

    @Test
    void duplicateAreaAddReturnsConflictAndDoesNotCreateSecondRow() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        service.addFavoriteArea(1L, areaRequest());

        assertThatThrownBy(() -> service.addFavoriteArea(1L, areaRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_AREA_ALREADY_EXISTS));
        assertThat(service.listFavoriteAreas(1L).items()).hasSize(1);
    }

    @Test
    void userCannotListOrDeleteAnotherUsersFavoriteArea() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);
        var created = service.addFavoriteArea(1L, areaRequest());

        assertThat(service.listFavoriteAreas(2L).items()).isEmpty();
        assertThatThrownBy(() -> service.removeFavoriteArea(2L, created.favoriteAreaId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_AREA_NOT_FOUND));
        assertThat(service.listFavoriteAreas(1L).items()).hasSize(1);
    }

    @Test
    void missingFavoriteAreaDeleteReturnsNotFound() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.removeFavoriteArea(1L, 404L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_AREA_NOT_FOUND));
    }

    @Test
    void favoriteAreaOperationsRequireUserId() {
        var mapper = new FakeFavoriteMapper();
        var service = new FavoriteService(mapper);

        assertThatThrownBy(() -> service.listFavoriteAreas(null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    private FavoriteAreaCreateRequest areaRequest() {
        return new FavoriteAreaCreateRequest(
                " 강남구  역삼동 ",
                "서울특별시",
                "강남구",
                "역삼동",
                new BigDecimal("37.5001000"),
                new BigDecimal("127.0364000"),
                5
        );
    }

    private static class FakeFavoriteMapper implements FavoriteMapper {

        private final Map<Long, PropertyType> propertyTypes = Map.of(
                1001L, PropertyType.APARTMENT,
                1912L, PropertyType.APARTMENT,
                1901L, PropertyType.OFFICETEL
        );
        private final Map<String, FavoriteApartmentRow> favoritesByUserAndProperty = new HashMap<>();
        private final Map<String, FavoriteAreaRow> areasByUserAndNormalizedKey = new HashMap<>();
        private final Map<Long, FavoriteAreaRow> areasById = new HashMap<>();
        private long nextFavoriteId = 1L;
        private long nextFavoriteAreaId = 801L;

        @Override
        public Optional<PropertyType> findPropertyTypeById(Long propertyId) {
            return Optional.ofNullable(propertyTypes.get(propertyId));
        }

        @Override
        public List<FavoriteApartmentRow> findFavoriteApartments(Long userId) {
            var prefix = userId + ":";
            return favoritesByUserAndProperty.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparing(FavoriteApartmentRow::getCreatedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<FavoriteApartmentRow> findFavoriteApartment(Long userId, Long propertyId) {
            return Optional.ofNullable(favoritesByUserAndProperty.get(key(userId, propertyId)));
        }

        @Override
        public int insertFavoriteApartment(Long userId, Long propertyId) {
            var key = key(userId, propertyId);
            if (favoritesByUserAndProperty.containsKey(key)) {
                return 0;
            }
            favoritesByUserAndProperty.put(key, row(userId, propertyId));
            return 1;
        }

        @Override
        public int deleteFavoriteApartment(Long userId, Long propertyId) {
            return favoritesByUserAndProperty.remove(key(userId, propertyId)) == null ? 0 : 1;
        }

        @Override
        public boolean existsFavoriteApartment(Long userId, Long propertyId) {
            return favoritesByUserAndProperty.containsKey(key(userId, propertyId));
        }

        @Override
        public List<FavoriteAreaRow> findFavoriteAreas(Long userId) {
            var prefix = userId + ":";
            return areasByUserAndNormalizedKey.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparing(FavoriteAreaRow::getCreatedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<FavoriteAreaRow> findFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
            return Optional.ofNullable(areasByUserAndNormalizedKey.get(key(userId, normalizedKey)));
        }

        @Override
        public int insertFavoriteArea(FavoriteAreaInsertCommand command) {
            var key = key(command.userId(), command.normalizedKey());
            if (areasByUserAndNormalizedKey.containsKey(key)) {
                return 0;
            }
            var row = areaRow(command);
            areasByUserAndNormalizedKey.put(key, row);
            areasById.put(row.getFavoriteAreaId(), row);
            return 1;
        }

        @Override
        public int deleteFavoriteArea(Long userId, Long favoriteAreaId) {
            var row = areasById.get(favoriteAreaId);
            if (row == null || !areasByUserAndNormalizedKey.containsKey(key(userId, row.getNormalizedKey()))) {
                return 0;
            }
            areasByUserAndNormalizedKey.remove(key(userId, row.getNormalizedKey()));
            areasById.remove(favoriteAreaId);
            return 1;
        }

        @Override
        public boolean existsFavoriteAreaByNormalizedKey(Long userId, String normalizedKey) {
            return areasByUserAndNormalizedKey.containsKey(key(userId, normalizedKey));
        }

        private FavoriteApartmentRow row(Long userId, Long propertyId) {
            var row = new FavoriteApartmentRow();
            row.setFavoriteId(nextFavoriteId++);
            row.setPropertyId(propertyId);
            row.setPropertyType(PropertyType.APARTMENT);
            row.setName(propertyId == 1912L ? "공공데이터 반영 아파트" : "샘플 역삼아파트");
            row.setAddress(propertyId == 1912L ? "서울특별시 송파구 잠실동 1" : "서울특별시 강남구 역삼동 12-3");
            row.setLatitude(new BigDecimal("37.5001000"));
            row.setLongitude(new BigDecimal("127.0364000"));
            row.setLatestTransactionType(TransactionType.SALE);
            row.setLatestDealAmount(1_250_000_000L);
            row.setLatestDealDate(LocalDate.of(2026, 5, 20));
            row.setCreatedAt(OffsetDateTime.parse("2026-06-15T16:00:00+09:00").plusSeconds(userId));
            return row;
        }

        private FavoriteAreaRow areaRow(FavoriteAreaInsertCommand command) {
            var row = new FavoriteAreaRow();
            row.setFavoriteAreaId(nextFavoriteAreaId++);
            row.setLabel(command.label());
            row.setSido(command.sido());
            row.setSigungu(command.sigungu());
            row.setLegalDong(command.legalDong());
            row.setCenterLat(command.centerLat());
            row.setCenterLng(command.centerLng());
            row.setZoomLevel(command.zoomLevel());
            row.setNormalizedKey(command.normalizedKey());
            row.setCreatedAt(OffsetDateTime.parse("2026-06-15T16:00:00+09:00").plusSeconds(command.userId()));
            return row;
        }

        private String key(Long userId, Object value) {
            return userId + ":" + value;
        }
    }
}
