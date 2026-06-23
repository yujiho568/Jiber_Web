package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath:/mapper/**/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
class PropertyMapperMyBatisTest {

    private static final LocalDate RECENT_SINCE = LocalDate.of(2025, 12, 23);

    @Autowired
    private PropertyMapper propertyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS property_transactions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS properties");
        jdbcTemplate.execute("""
                CREATE TABLE properties (
                    property_id BIGINT NOT NULL,
                    property_type VARCHAR(30) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    sido VARCHAR(50) NOT NULL,
                    sigungu VARCHAR(50) NOT NULL,
                    legal_dong VARCHAR(50) NOT NULL,
                    road_address VARCHAR(255),
                    jibun_address VARCHAR(255),
                    latitude DECIMAL(10, 7),
                    longitude DECIMAL(10, 7),
                    built_year INT,
                    household_count INT,
                    PRIMARY KEY (property_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE property_transactions (
                    transaction_id BIGINT NOT NULL,
                    property_id BIGINT NOT NULL,
                    transaction_type VARCHAR(30) NOT NULL,
                    exclusive_area_m2 DECIMAL(10, 2),
                    floor INT,
                    deal_amount_krw BIGINT,
                    deposit_amount_krw BIGINT,
                    monthly_rent_krw BIGINT,
                    deal_date DATE NOT NULL,
                    PRIMARY KEY (transaction_id)
                )
                """);
    }

    @Test
    void findMapPropertiesMapsRecentTransactionCountUsingRecentSinceAndCurrentFilters() {
        insertProperty(1L, PropertyType.APARTMENT, "역삼 A", "서울특별시", "강남구", "역삼동", "37.5000000", "127.0300000");
        insertProperty(2L, PropertyType.APARTMENT, "역삼 B", "서울특별시", "강남구", "역삼동", "37.5100000", "127.0400000");
        insertProperty(9L, PropertyType.APARTMENT, "부산 외부", "부산광역시", "해운대구", "우동", "35.1600000", "129.1600000");
        insertTransaction(101L, 1L, TransactionType.SALE, 700_000_000L, null, LocalDate.of(2026, 1, 10));
        insertTransaction(102L, 1L, TransactionType.SALE, null, null, LocalDate.of(2026, 2, 10));
        insertTransaction(103L, 1L, TransactionType.SALE, 600_000_000L, null, LocalDate.of(2025, 1, 10));
        insertTransaction(104L, 1L, TransactionType.JEONSE, null, 400_000_000L, LocalDate.of(2026, 3, 10));
        insertTransaction(201L, 2L, TransactionType.SALE, 800_000_000L, null, LocalDate.of(2025, 11, 10));
        insertTransaction(901L, 9L, TransactionType.SALE, 900_000_000L, null, LocalDate.of(2026, 1, 10));

        var rows = propertyMapper.findMapProperties(mapRequest(7), RECENT_SINCE);

        assertThat(rows).extracting(PropertyListRow::getPropertyId).containsExactly(1L, 2L);
        assertThat(row(rows, 1L).getRecentTransactionCount()).isEqualTo(2);
        assertThat(row(rows, 2L).getRecentTransactionCount()).isZero();
    }

    @Test
    void findLegalDongClustersAggregatesVisiblePropertiesBySidoSigunguLegalDong() {
        insertProperty(1L, PropertyType.APARTMENT, "역삼 A", "서울특별시", "강남구", "역삼동", "37.5000000", "127.0300000");
        insertProperty(2L, PropertyType.APARTMENT, "역삼 B", "서울특별시", "강남구", "역삼동", "37.5200000", "127.0500000");
        insertProperty(3L, PropertyType.APARTMENT, "삼성 A", "서울특별시", "강남구", "삼성동", "37.5100000", "127.0600000");
        insertProperty(4L, PropertyType.VILLA, "역삼 빌라", "서울특별시", "강남구", "역삼동", "37.5300000", "127.0700000");
        insertProperty(9L, PropertyType.APARTMENT, "부산 외부", "부산광역시", "해운대구", "우동", "35.1600000", "129.1600000");
        insertTransaction(101L, 1L, TransactionType.SALE, 700_000_000L, null, LocalDate.of(2026, 1, 10));
        insertTransaction(102L, 1L, TransactionType.SALE, null, null, LocalDate.of(2026, 2, 10));
        insertTransaction(103L, 1L, TransactionType.SALE, 500_000_000L, null, LocalDate.of(2025, 1, 10));
        insertTransaction(201L, 2L, TransactionType.SALE, null, 300_000_000L, LocalDate.of(2026, 1, 20));
        insertTransaction(202L, 2L, TransactionType.JEONSE, null, 200_000_000L, LocalDate.of(2026, 1, 20));
        insertTransaction(301L, 3L, TransactionType.SALE, 600_000_000L, null, LocalDate.of(2026, 3, 10));
        insertTransaction(401L, 4L, TransactionType.SALE, 100_000_000L, null, LocalDate.of(2026, 1, 10));
        insertTransaction(901L, 9L, TransactionType.SALE, 900_000_000L, null, LocalDate.of(2026, 1, 10));

        var rows = propertyMapper.findLegalDongClusters(mapRequest(5), RECENT_SINCE);

        assertThat(rows).extracting(AdministrativeClusterRow::getLegalDong).containsExactly("역삼동", "삼성동");
        var yeoksam = cluster(rows, "역삼동");
        assertThat(yeoksam.getSido()).isEqualTo("서울특별시");
        assertThat(yeoksam.getSigungu()).isEqualTo("강남구");
        assertThat(yeoksam.getLabel()).isEqualTo("역삼동");
        assertThat(yeoksam.getCenterLat()).isEqualByComparingTo("37.5100000");
        assertThat(yeoksam.getCenterLng()).isEqualByComparingTo("127.0400000");
        assertThat(yeoksam.getPropertyCount()).isEqualTo(2);
        assertThat(yeoksam.getTransactionCount()).isEqualTo(3);
        assertThat(yeoksam.getAverageDealAmount()).isEqualTo(500_000_000L);
    }

    @Test
    void findSigunguClustersAggregatesVisiblePropertiesBySidoSigungu() {
        insertProperty(1L, PropertyType.APARTMENT, "역삼 A", "서울특별시", "강남구", "역삼동", "37.5000000", "127.0300000");
        insertProperty(2L, PropertyType.APARTMENT, "삼성 A", "서울특별시", "강남구", "삼성동", "37.5200000", "127.0500000");
        insertProperty(3L, PropertyType.APARTMENT, "서초 A", "서울특별시", "서초구", "서초동", "37.4900000", "127.0100000");
        insertProperty(9L, PropertyType.APARTMENT, "부산 외부", "부산광역시", "해운대구", "우동", "35.1600000", "129.1600000");
        insertTransaction(101L, 1L, TransactionType.SALE, 700_000_000L, null, LocalDate.of(2026, 1, 10));
        insertTransaction(102L, 1L, TransactionType.SALE, 500_000_000L, null, LocalDate.of(2025, 1, 10));
        insertTransaction(201L, 2L, TransactionType.SALE, null, 300_000_000L, LocalDate.of(2026, 1, 20));
        insertTransaction(301L, 3L, TransactionType.SALE, null, null, LocalDate.of(2026, 2, 10));
        insertTransaction(901L, 9L, TransactionType.SALE, 900_000_000L, null, LocalDate.of(2026, 1, 10));

        var rows = propertyMapper.findSigunguClusters(mapRequest(7), RECENT_SINCE);

        assertThat(rows).extracting(AdministrativeClusterRow::getSigungu).containsExactly("강남구", "서초구");
        var gangnam = cluster(rows, "강남구");
        assertThat(gangnam.getSido()).isEqualTo("서울특별시");
        assertThat(gangnam.getLegalDong()).isNull();
        assertThat(gangnam.getLabel()).isEqualTo("강남구");
        assertThat(gangnam.getCenterLat()).isEqualByComparingTo("37.5100000");
        assertThat(gangnam.getCenterLng()).isEqualByComparingTo("127.0400000");
        assertThat(gangnam.getPropertyCount()).isEqualTo(2);
        assertThat(gangnam.getTransactionCount()).isEqualTo(2);
        assertThat(gangnam.getAverageDealAmount()).isEqualTo(500_000_000L);
        assertThat(cluster(rows, "서초구").getTransactionCount()).isEqualTo(1);
        assertThat(cluster(rows, "서초구").getAverageDealAmount()).isNull();
    }

    private MapSearchRequest mapRequest(int zoomLevel) {
        return new MapSearchRequest(
                new BigDecimal("37.4500000"),
                new BigDecimal("126.9500000"),
                new BigDecimal("37.6000000"),
                new BigDecimal("127.1500000"),
                zoomLevel,
                List.of(PropertyType.APARTMENT),
                List.of(TransactionType.SALE),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void insertProperty(
            Long propertyId,
            PropertyType propertyType,
            String name,
            String sido,
            String sigungu,
            String legalDong,
            String latitude,
            String longitude
    ) {
        jdbcTemplate.update("""
                        INSERT INTO properties (
                            property_id, property_type, name, sido, sigungu, legal_dong,
                            road_address, jibun_address, latitude, longitude, built_year, household_count
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                propertyId,
                propertyType.name(),
                name,
                sido,
                sigungu,
                legalDong,
                sido + " " + sigungu + " " + legalDong,
                null,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                2000,
                100
        );
    }

    private void insertTransaction(
            Long transactionId,
            Long propertyId,
            TransactionType transactionType,
            Long dealAmount,
            Long depositAmount,
            LocalDate dealDate
    ) {
        jdbcTemplate.update("""
                        INSERT INTO property_transactions (
                            transaction_id, property_id, transaction_type, exclusive_area_m2, floor,
                            deal_amount_krw, deposit_amount_krw, monthly_rent_krw, deal_date
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                propertyId,
                transactionType.name(),
                new BigDecimal("84.50"),
                10,
                dealAmount,
                depositAmount,
                null,
                dealDate
        );
    }

    private PropertyListRow row(List<PropertyListRow> rows, Long propertyId) {
        return rows.stream()
                .filter(row -> propertyId.equals(row.getPropertyId()))
                .findFirst()
                .orElseThrow();
    }

    private AdministrativeClusterRow cluster(List<AdministrativeClusterRow> rows, String label) {
        return rows.stream()
                .filter(row -> label.equals(row.getLabel()))
                .findFirst()
                .orElseThrow();
    }
}
