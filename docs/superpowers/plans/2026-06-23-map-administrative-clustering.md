# Map Administrative Clustering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build level-aware map clustering that shows Kakao MarkerClusterer recent 6-month transaction-count badges from level 4 upward and administrative average-price clusters from level 5 upward.

**Architecture:** The backend extends the existing `/api/v1/properties/map` contract with per-item `recentTransactionCount` and `administrativeClusters`. The frontend keeps the existing map/list workflow, then switches map layers by Kakao map level: individual property markers for levels 1-3, MarkerClusterer transaction badges for levels 4+, and administrative custom overlays for levels 5+.

**Tech Stack:** Spring Boot 3, MyBatis, MySQL-compatible SQL, Vue 3, TypeScript, Vitest, Kakao Maps JavaScript SDK MarkerClusterer.

---

## File Structure

Backend files:

- Modify `backend/src/main/java/com/jiber/backend/property/PropertyMapItemResponse.java`: add `recentTransactionCount`.
- Modify `backend/src/main/java/com/jiber/backend/property/PropertyMapResponse.java`: add `administrativeClusters`.
- Create `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterLevel.java`: legal-dong and sigungu enum.
- Create `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterResponse.java`: public map response DTO.
- Create `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterRow.java`: MyBatis row object.
- Modify `backend/src/main/java/com/jiber/backend/property/PropertyListRow.java`: add `recentTransactionCount`.
- Modify `backend/src/main/java/com/jiber/backend/property/PropertyMapper.java`: add legal-dong and sigungu cluster queries.
- Modify `backend/src/main/java/com/jiber/backend/property/PropertyService.java`: map rows and select administrative cluster level.
- Modify `backend/src/main/resources/mapper/PropertyMapper.xml`: add recent count column and cluster aggregation SQL.
- Modify `backend/src/test/java/com/jiber/backend/property/PropertyServiceTest.java`: service-level contract tests.
- Create `backend/src/test/java/com/jiber/backend/property/PropertyMapperMyBatisTest.java`: mapper-level aggregation tests.
- Modify `docs/contracts/property-api.md`: document response extension.

Frontend files:

- Modify `frontend/src/api/types.ts`: add `AdministrativeCluster` and `recentTransactionCount`.
- Modify `frontend/src/map/kakaoMap.ts`: add render-mode helpers, MarkerClusterer synchronization, and administrative overlay synchronization.
- Modify `frontend/src/map/__tests__/kakaoMap.test.ts`: unit tests for render mode, cluster text, and administrative labels.
- Modify `frontend/src/components/KakaoMapPanel.vue`: hold three map layers and render them by map level.
- Modify `frontend/src/components/__tests__/KakaoMapPanel.test.ts`: component tests for levels 1-3, 4, 5-6, and 7+.
- Modify `frontend/src/views/MapView.vue`: store and pass `administrativeClusters`.
- Modify `frontend/src/views/__tests__/mapAndDetailViews.test.ts`: update map response helpers and assert clusters are passed through.

Do not edit unrelated dirty files. Before each commit, run `git status --short` and stage only files listed in that task.

---

### Task 1: Backend Response Contract And Service Selection

**Files:**
- Create: `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterLevel.java`
- Create: `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterResponse.java`
- Create: `backend/src/main/java/com/jiber/backend/property/AdministrativeClusterRow.java`
- Modify: `backend/src/main/java/com/jiber/backend/property/PropertyMapItemResponse.java`
- Modify: `backend/src/main/java/com/jiber/backend/property/PropertyMapResponse.java`
- Modify: `backend/src/main/java/com/jiber/backend/property/PropertyListRow.java`
- Modify: `backend/src/main/java/com/jiber/backend/property/PropertyMapper.java`
- Modify: `backend/src/main/java/com/jiber/backend/property/PropertyService.java`
- Test: `backend/src/test/java/com/jiber/backend/property/PropertyServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Add these tests to `PropertyServiceTest`:

```java
@Test
void mapPropertiesIncludesRecentTransactionCountAndNoAdministrativeClustersAtLevelFour() {
    var mapper = new FakePropertyMapper();
    var row = sampleListRow();
    row.setRecentTransactionCount(6);
    mapper.mapRows.add(row);
    var service = service(mapper, new RecordingValuationClient());
    var request = mapRequest(4);

    var response = service.findMapProperties(request);

    assertThat(response.items()).singleElement().satisfies(item -> {
        assertThat(item.propertyId()).isEqualTo(1001L);
        assertThat(item.recentTransactionCount()).isEqualTo(6);
    });
    assertThat(response.administrativeClusters()).isEmpty();
    assertThat(mapper.legalDongClusterRequest).isNull();
    assertThat(mapper.sigunguClusterRequest).isNull();
}

@Test
void mapPropertiesReturnsLegalDongAdministrativeClustersAtLevelsFiveAndSix() {
    var mapper = new FakePropertyMapper();
    mapper.legalDongClusters.add(sampleClusterRow(
            AdministrativeClusterLevel.LEGAL_DONG,
            "서울특별시",
            "종로구",
            "무악동",
            "무악동",
            12,
            31,
            1_080_000_000L
    ));
    var service = service(mapper, new RecordingValuationClient());
    var request = mapRequest(5);

    var response = service.findMapProperties(request);

    assertThat(response.administrativeClusters()).singleElement().satisfies(cluster -> {
        assertThat(cluster.clusterId()).isEqualTo("LEGAL_DONG:서울특별시:종로구:무악동");
        assertThat(cluster.level()).isEqualTo(AdministrativeClusterLevel.LEGAL_DONG);
        assertThat(cluster.label()).isEqualTo("무악동");
        assertThat(cluster.transactionCount()).isEqualTo(31);
        assertThat(cluster.averageDealAmount()).isEqualTo(1_080_000_000L);
    });
    assertThat(mapper.legalDongClusterRequest).isSameAs(request);
    assertThat(mapper.sigunguClusterRequest).isNull();
}

@Test
void mapPropertiesReturnsSigunguAdministrativeClustersAtLevelSevenAndAbove() {
    var mapper = new FakePropertyMapper();
    mapper.sigunguClusters.add(sampleClusterRow(
            AdministrativeClusterLevel.SIGUNGU,
            "서울특별시",
            "종로구",
            null,
            "종로구",
            42,
            88,
            965_000_000L
    ));
    var service = service(mapper, new RecordingValuationClient());
    var request = mapRequest(7);

    var response = service.findMapProperties(request);

    assertThat(response.administrativeClusters()).singleElement().satisfies(cluster -> {
        assertThat(cluster.clusterId()).isEqualTo("SIGUNGU:서울특별시:종로구");
        assertThat(cluster.level()).isEqualTo(AdministrativeClusterLevel.SIGUNGU);
        assertThat(cluster.legalDong()).isNull();
        assertThat(cluster.label()).isEqualTo("종로구");
        assertThat(cluster.propertyCount()).isEqualTo(42);
    });
    assertThat(mapper.legalDongClusterRequest).isNull();
    assertThat(mapper.sigunguClusterRequest).isSameAs(request);
}
```

Add these helpers to `PropertyServiceTest`:

```java
private MapSearchRequest mapRequest(int zoomLevel) {
    return new MapSearchRequest(
            new BigDecimal("37.40"),
            new BigDecimal("126.90"),
            new BigDecimal("37.60"),
            new BigDecimal("127.20"),
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

private AdministrativeClusterRow sampleClusterRow(
        AdministrativeClusterLevel level,
        String sido,
        String sigungu,
        String legalDong,
        String label,
        int propertyCount,
        int transactionCount,
        Long averageDealAmount
) {
    var row = new AdministrativeClusterRow();
    row.setLevel(level);
    row.setSido(sido);
    row.setSigungu(sigungu);
    row.setLegalDong(legalDong);
    row.setLabel(label);
    row.setCenterLat(new BigDecimal("37.5738636"));
    row.setCenterLng(new BigDecimal("126.9594466"));
    row.setPropertyCount(propertyCount);
    row.setTransactionCount(transactionCount);
    row.setAverageDealAmount(averageDealAmount);
    return row;
}
```

Extend `FakePropertyMapper` in `PropertyServiceTest`:

```java
private final List<AdministrativeClusterRow> legalDongClusters = new ArrayList<>();
private final List<AdministrativeClusterRow> sigunguClusters = new ArrayList<>();
private MapSearchRequest legalDongClusterRequest;
private MapSearchRequest sigunguClusterRequest;
private LocalDate mapRecentSince;
private LocalDate legalDongRecentSince;
private LocalDate sigunguRecentSince;

@Override
public List<PropertyListRow> findMapProperties(MapSearchRequest request, LocalDate recentSince) {
    this.mapRecentSince = recentSince;
    return mapRows;
}

@Override
public List<AdministrativeClusterRow> findLegalDongClusters(MapSearchRequest request, LocalDate recentSince) {
    this.legalDongClusterRequest = request;
    this.legalDongRecentSince = recentSince;
    return legalDongClusters;
}

@Override
public List<AdministrativeClusterRow> findSigunguClusters(MapSearchRequest request, LocalDate recentSince) {
    this.sigunguClusterRequest = request;
    this.sigunguRecentSince = recentSince;
    return sigunguClusters;
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=PropertyServiceTest test
```

Expected: FAIL at compilation because `AdministrativeClusterRow`, `AdministrativeClusterLevel`, `recentTransactionCount()`, `administrativeClusters()`, and new mapper methods do not exist.

- [ ] **Step 3: Add the backend DTOs and service mapping**

Create `AdministrativeClusterLevel.java`:

```java
package com.jiber.backend.property;

public enum AdministrativeClusterLevel {
    LEGAL_DONG,
    SIGUNGU
}
```

Create `AdministrativeClusterResponse.java`:

```java
package com.jiber.backend.property;

public record AdministrativeClusterResponse(
        String clusterId,
        AdministrativeClusterLevel level,
        String sido,
        String sigungu,
        String legalDong,
        String label,
        Double centerLat,
        Double centerLng,
        Integer propertyCount,
        Integer transactionCount,
        Long averageDealAmount
) {
}
```

Create `AdministrativeClusterRow.java`:

```java
package com.jiber.backend.property;

import java.math.BigDecimal;

public class AdministrativeClusterRow {

    private AdministrativeClusterLevel level;
    private String sido;
    private String sigungu;
    private String legalDong;
    private String label;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
    private Integer propertyCount;
    private Integer transactionCount;
    private Long averageDealAmount;

    public AdministrativeClusterLevel getLevel() {
        return level;
    }

    public void setLevel(AdministrativeClusterLevel level) {
        this.level = level;
    }

    public String getSido() {
        return sido;
    }

    public void setSido(String sido) {
        this.sido = sido;
    }

    public String getSigungu() {
        return sigungu;
    }

    public void setSigungu(String sigungu) {
        this.sigungu = sigungu;
    }

    public String getLegalDong() {
        return legalDong;
    }

    public void setLegalDong(String legalDong) {
        this.legalDong = legalDong;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getCenterLat() {
        return centerLat;
    }

    public void setCenterLat(BigDecimal centerLat) {
        this.centerLat = centerLat;
    }

    public BigDecimal getCenterLng() {
        return centerLng;
    }

    public void setCenterLng(BigDecimal centerLng) {
        this.centerLng = centerLng;
    }

    public Integer getPropertyCount() {
        return propertyCount;
    }

    public void setPropertyCount(Integer propertyCount) {
        this.propertyCount = propertyCount;
    }

    public Integer getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Integer transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Long getAverageDealAmount() {
        return averageDealAmount;
    }

    public void setAverageDealAmount(Long averageDealAmount) {
        this.averageDealAmount = averageDealAmount;
    }
}
```

Replace `PropertyMapItemResponse.java` with:

```java
package com.jiber.backend.property;

public record PropertyMapItemResponse(
        Long propertyId,
        PropertyType propertyType,
        String name,
        String address,
        Double lat,
        Double lng,
        LatestTransactionResponse latestTransaction,
        Integer dealCount,
        Integer recentTransactionCount,
        boolean aiAvailable
) {
}
```

Replace `PropertyMapResponse.java` with:

```java
package com.jiber.backend.property;

import java.util.List;

public record PropertyMapResponse(
        List<PropertyMapItemResponse> items,
        List<AdministrativeClusterResponse> administrativeClusters,
        BoundsResponse bounds,
        MapFilterResponse filters
) {
}
```

Add this field and accessors to `PropertyListRow.java`:

```java
private Integer recentTransactionCount;

public Integer getRecentTransactionCount() {
    return recentTransactionCount;
}

public void setRecentTransactionCount(Integer recentTransactionCount) {
    this.recentTransactionCount = recentTransactionCount;
}
```

Add these methods to `PropertyMapper.java`:

```java
import java.time.LocalDate;

List<PropertyListRow> findMapProperties(
        @Param("request") MapSearchRequest request,
        @Param("recentSince") LocalDate recentSince
);

List<AdministrativeClusterRow> findLegalDongClusters(
        @Param("request") MapSearchRequest request,
        @Param("recentSince") LocalDate recentSince
);

List<AdministrativeClusterRow> findSigunguClusters(
        @Param("request") MapSearchRequest request,
        @Param("recentSince") LocalDate recentSince
);
```

Update `PropertyService.findMapProperties` and add helpers:

```java
import java.time.LocalDate;

public PropertyMapResponse findMapProperties(MapSearchRequest request) {
    var recentSince = LocalDate.now().minusMonths(6);
    return new PropertyMapResponse(
            propertyMapper.findMapProperties(request, recentSince).stream()
                    .map(this::toMapItem)
                    .toList(),
            findAdministrativeClusters(request, recentSince),
            new BoundsResponse(request.swLat(), request.swLng(), request.neLat(), request.neLng()),
            new MapFilterResponse(request.propertyTypes(), request.transactionTypes(), request.zoomLevel())
    );
}

private List<AdministrativeClusterResponse> findAdministrativeClusters(MapSearchRequest request, LocalDate recentSince) {
    if (request.zoomLevel() == null || request.zoomLevel() < 5) {
        return List.of();
    }
    var rows = request.zoomLevel() >= 7
            ? propertyMapper.findSigunguClusters(request, recentSince)
            : propertyMapper.findLegalDongClusters(request, recentSince);
    return rows.stream()
            .map(this::toAdministrativeCluster)
            .toList();
}

private AdministrativeClusterResponse toAdministrativeCluster(AdministrativeClusterRow row) {
    return new AdministrativeClusterResponse(
            clusterId(row),
            row.getLevel(),
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

private String clusterId(AdministrativeClusterRow row) {
    if (row.getLevel() == AdministrativeClusterLevel.SIGUNGU) {
        return "SIGUNGU:" + row.getSido() + ":" + row.getSigungu();
    }
    return "LEGAL_DONG:" + row.getSido() + ":" + row.getSigungu() + ":" + row.getLegalDong();
}
```

Update `toMapItem` constructor call:

```java
return new PropertyMapItemResponse(
        row.getPropertyId(),
        row.getPropertyType(),
        row.getName(),
        row.getAddress(),
        toDouble(row.getLatitude()),
        toDouble(row.getLongitude()),
        toLatestTransaction(row),
        row.getDealCount() == null ? 0 : row.getDealCount(),
        row.getRecentTransactionCount() == null ? 0 : row.getRecentTransactionCount(),
        row.getPropertyType() == PropertyType.APARTMENT
);
```

- [ ] **Step 4: Run the service test to verify it passes**

Run:

```powershell
cd backend
mvn -Dtest=PropertyServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit backend contract and service selection**

Run:

```powershell
git status --short
git add backend/src/main/java/com/jiber/backend/property/AdministrativeClusterLevel.java `
  backend/src/main/java/com/jiber/backend/property/AdministrativeClusterResponse.java `
  backend/src/main/java/com/jiber/backend/property/AdministrativeClusterRow.java `
  backend/src/main/java/com/jiber/backend/property/PropertyMapItemResponse.java `
  backend/src/main/java/com/jiber/backend/property/PropertyMapResponse.java `
  backend/src/main/java/com/jiber/backend/property/PropertyListRow.java `
  backend/src/main/java/com/jiber/backend/property/PropertyMapper.java `
  backend/src/main/java/com/jiber/backend/property/PropertyService.java `
  backend/src/test/java/com/jiber/backend/property/PropertyServiceTest.java
git commit -m "feat: add map administrative cluster contract"
```

---

### Task 2: Backend SQL Aggregation

**Files:**
- Modify: `backend/src/main/resources/mapper/PropertyMapper.xml`
- Test: `backend/src/test/java/com/jiber/backend/property/PropertyMapperMyBatisTest.java`

- [ ] **Step 1: Write the failing MyBatis aggregation test**

Create `PropertyMapperMyBatisTest.java`:

```java
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

    private final LocalDate recentSince = LocalDate.now().minusMonths(6);

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
                    property_id BIGINT NOT NULL AUTO_INCREMENT,
                    property_type VARCHAR(30) NOT NULL,
                    name VARCHAR(200) NOT NULL,
                    sido VARCHAR(50),
                    sigungu VARCHAR(50),
                    legal_dong VARCHAR(50),
                    road_address VARCHAR(255),
                    jibun_address VARCHAR(255),
                    latitude DECIMAL(12, 8),
                    longitude DECIMAL(12, 8),
                    built_year INT,
                    household_count INT,
                    PRIMARY KEY (property_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE property_transactions (
                    transaction_id BIGINT NOT NULL AUTO_INCREMENT,
                    property_id BIGINT NOT NULL,
                    transaction_type VARCHAR(30) NOT NULL,
                    exclusive_area_m2 DECIMAL(10, 4),
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
    void mapItemsExposeRecentSixMonthTransactionCount() {
        insertProperty(1001L, "APARTMENT", "경희궁롯데캐슬", "서울특별시", "종로구", "무악동", "37.57386360", "126.95944660");
        insertTransaction(1001L, "SALE", 1_200_000_000L, null, LocalDate.now());
        insertTransaction(1001L, "SALE", 1_100_000_000L, null, LocalDate.now().minusMonths(1));
        insertTransaction(1001L, "SALE", 900_000_000L, null, LocalDate.now().minusMonths(7));
        var request = mapRequest(4);

        var rows = propertyMapper.findMapProperties(request, recentSince);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPropertyId()).isEqualTo(1001L);
            assertThat(row.getDealCount()).isEqualTo(3);
            assertThat(row.getRecentTransactionCount()).isEqualTo(2);
        });
    }

    @Test
    void legalDongClustersAggregateRecentSixMonthAverageAndCount() {
        insertProperty(1001L, "APARTMENT", "경희궁롯데캐슬", "서울특별시", "종로구", "무악동", "37.57386360", "126.95944660");
        insertProperty(1002L, "APARTMENT", "무악현대", "서울특별시", "종로구", "무악동", "37.57450000", "126.96000000");
        insertTransaction(1001L, "SALE", 1_200_000_000L, null, LocalDate.now());
        insertTransaction(1002L, "SALE", 800_000_000L, null, LocalDate.now().minusMonths(2));
        insertTransaction(1002L, "SALE", 700_000_000L, null, LocalDate.now().minusMonths(8));
        var request = mapRequest(5);

        var clusters = propertyMapper.findLegalDongClusters(request, recentSince);

        assertThat(clusters).singleElement().satisfies(cluster -> {
            assertThat(cluster.getLevel()).isEqualTo(AdministrativeClusterLevel.LEGAL_DONG);
            assertThat(cluster.getLabel()).isEqualTo("무악동");
            assertThat(cluster.getPropertyCount()).isEqualTo(2);
            assertThat(cluster.getTransactionCount()).isEqualTo(2);
            assertThat(cluster.getAverageDealAmount()).isEqualTo(1_000_000_000L);
        });
    }

    @Test
    void sigunguClustersAggregateRecentSixMonthAverageAndCount() {
        insertProperty(1001L, "APARTMENT", "경희궁롯데캐슬", "서울특별시", "종로구", "무악동", "37.57386360", "126.95944660");
        insertProperty(1003L, "APARTMENT", "광화문풍림", "서울특별시", "종로구", "사직동", "37.57500000", "126.97000000");
        insertTransaction(1001L, "JEONSE", null, 900_000_000L, LocalDate.now());
        insertTransaction(1003L, "JEONSE", null, 700_000_000L, LocalDate.now().minusMonths(3));
        var request = new MapSearchRequest(
                new BigDecimal("37.40"),
                new BigDecimal("126.80"),
                new BigDecimal("37.70"),
                new BigDecimal("127.10"),
                7,
                List.of(PropertyType.APARTMENT),
                List.of(TransactionType.JEONSE),
                null,
                null,
                null,
                null,
                null,
                null
        );

        var clusters = propertyMapper.findSigunguClusters(request, recentSince);

        assertThat(clusters).singleElement().satisfies(cluster -> {
            assertThat(cluster.getLevel()).isEqualTo(AdministrativeClusterLevel.SIGUNGU);
            assertThat(cluster.getLabel()).isEqualTo("종로구");
            assertThat(cluster.getLegalDong()).isNull();
            assertThat(cluster.getPropertyCount()).isEqualTo(2);
            assertThat(cluster.getTransactionCount()).isEqualTo(2);
            assertThat(cluster.getAverageDealAmount()).isEqualTo(800_000_000L);
        });
    }

    private MapSearchRequest mapRequest(int zoomLevel) {
        return new MapSearchRequest(
                new BigDecimal("37.40"),
                new BigDecimal("126.80"),
                new BigDecimal("37.70"),
                new BigDecimal("127.10"),
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
            String propertyType,
            String name,
            String sido,
            String sigungu,
            String legalDong,
            String latitude,
            String longitude
    ) {
        jdbcTemplate.update("""
                INSERT INTO properties
                    (property_id, property_type, name, sido, sigungu, legal_dong, latitude, longitude)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, propertyId, propertyType, name, sido, sigungu, legalDong, new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private void insertTransaction(
            Long propertyId,
            String transactionType,
            Long dealAmount,
            Long depositAmount,
            LocalDate dealDate
    ) {
        jdbcTemplate.update("""
                INSERT INTO property_transactions
                    (property_id, transaction_type, exclusive_area_m2, floor, deal_amount_krw, deposit_amount_krw, monthly_rent_krw, deal_date)
                VALUES (?, ?, 84.9500, 10, ?, ?, 0, ?)
                """,
                propertyId,
                transactionType,
                dealAmount,
                depositAmount,
                dealDate
        );
    }
}
```

- [ ] **Step 2: Run the mapper test to verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=PropertyMapperMyBatisTest test
```

Expected: FAIL because `recent_transaction_count`, `findLegalDongClusters`, and `findSigunguClusters` SQL are not mapped.

- [ ] **Step 3: Add the SQL implementation**

In `PropertyMapper.xml`, add this SQL fragment:

```xml
<sql id="RecentTransactionFilters">
    AND t.deal_date &gt;= #{recentSince}
    <include refid="TransactionFilters"/>
</sql>
```

In `ListColumns`, add this column after `deal_count`:

```xml
,
(
    SELECT COUNT(*)
    FROM property_transactions t
    WHERE t.property_id = p.property_id
    <include refid="RecentTransactionFilters"/>
) AS recent_transaction_count
```

Add these selects before `findDetailById`:

```xml
<select id="findLegalDongClusters" resultType="com.jiber.backend.property.AdministrativeClusterRow">
    SELECT
        'LEGAL_DONG' AS level,
        p.sido,
        p.sigungu,
        p.legal_dong,
        p.legal_dong AS label,
        AVG(p.latitude) AS center_lat,
        AVG(p.longitude) AS center_lng,
        COUNT(DISTINCT p.property_id) AS property_count,
        COUNT(t.transaction_id) AS transaction_count,
        CAST(ROUND(AVG(COALESCE(t.deal_amount_krw, t.deposit_amount_krw))) AS SIGNED) AS average_deal_amount
    FROM properties p
    JOIN property_transactions t ON t.property_id = p.property_id
    <include refid="MapWhere"/>
      AND p.sido IS NOT NULL
      AND p.sigungu IS NOT NULL
      AND p.legal_dong IS NOT NULL
      <include refid="RecentTransactionFilters"/>
    GROUP BY p.sido, p.sigungu, p.legal_dong
    HAVING COUNT(t.transaction_id) &gt; 0
    ORDER BY transaction_count DESC, p.sido ASC, p.sigungu ASC, p.legal_dong ASC
</select>

<select id="findSigunguClusters" resultType="com.jiber.backend.property.AdministrativeClusterRow">
    SELECT
        'SIGUNGU' AS level,
        p.sido,
        p.sigungu,
        NULL AS legal_dong,
        p.sigungu AS label,
        AVG(p.latitude) AS center_lat,
        AVG(p.longitude) AS center_lng,
        COUNT(DISTINCT p.property_id) AS property_count,
        COUNT(t.transaction_id) AS transaction_count,
        CAST(ROUND(AVG(COALESCE(t.deal_amount_krw, t.deposit_amount_krw))) AS SIGNED) AS average_deal_amount
    FROM properties p
    JOIN property_transactions t ON t.property_id = p.property_id
    <include refid="MapWhere"/>
      AND p.sido IS NOT NULL
      AND p.sigungu IS NOT NULL
      <include refid="RecentTransactionFilters"/>
    GROUP BY p.sido, p.sigungu
    HAVING COUNT(t.transaction_id) &gt; 0
    ORDER BY transaction_count DESC, p.sido ASC, p.sigungu ASC
</select>
```

If MyBatis XML rejects `<include refid="MapWhere"/>` inside a joined query because `MapWhere` starts with `WHERE`, keep it and do not add a second `WHERE`. The query shape above intentionally places the transaction join before the existing map-bound `WHERE`.

- [ ] **Step 4: Run backend tests to verify they pass**

Run:

```powershell
cd backend
mvn -Dtest=PropertyMapperMyBatisTest,PropertyServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit backend SQL aggregation**

Run:

```powershell
git status --short
git add backend/src/main/resources/mapper/PropertyMapper.xml `
  backend/src/test/java/com/jiber/backend/property/PropertyMapperMyBatisTest.java
git commit -m "feat: aggregate map cluster statistics"
```

---

### Task 3: Frontend Types And Pure Map Helpers

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/map/kakaoMap.ts`
- Test: `frontend/src/map/__tests__/kakaoMap.test.ts`

- [ ] **Step 1: Write failing frontend helper tests**

Add these imports to `kakaoMap.test.ts`:

```ts
import {
  formatAdministrativeClusterLabel,
  mapMarkerRenderMode,
  sumRecentTransactionCount
} from '@/map/kakaoMap'
```

Add these tests:

```ts
it('selects map marker render mode by Kakao zoom level', () => {
  expect(mapMarkerRenderMode(3)).toEqual({
    showIndividualMarkers: true,
    showTransactionClusterer: false,
    showAdministrativeClusters: false
  })
  expect(mapMarkerRenderMode(4)).toEqual({
    showIndividualMarkers: false,
    showTransactionClusterer: true,
    showAdministrativeClusters: false
  })
  expect(mapMarkerRenderMode(5)).toEqual({
    showIndividualMarkers: false,
    showTransactionClusterer: true,
    showAdministrativeClusters: true
  })
  expect(mapMarkerRenderMode(7)).toEqual({
    showIndividualMarkers: false,
    showTransactionClusterer: true,
    showAdministrativeClusters: true
  })
})

it('formats administrative cluster labels in Korean', () => {
  expect(formatAdministrativeClusterLabel({
    clusterId: 'LEGAL_DONG:서울특별시:종로구:무악동',
    level: 'LEGAL_DONG',
    sido: '서울특별시',
    sigungu: '종로구',
    legalDong: '무악동',
    label: '무악동',
    centerLat: 37.5738636,
    centerLng: 126.9594466,
    propertyCount: 12,
    transactionCount: 31,
    averageDealAmount: 1080000000
  })).toBe('무악동\n평균 10.8억원\n거래 31건')

  expect(formatAdministrativeClusterLabel({
    clusterId: 'SIGUNGU:서울특별시:종로구',
    level: 'SIGUNGU',
    sido: '서울특별시',
    sigungu: '종로구',
    legalDong: null,
    label: '종로구',
    centerLat: 37.57,
    centerLng: 126.96,
    propertyCount: 21,
    transactionCount: 0,
    averageDealAmount: null
  })).toBe('종로구\n평균 정보 없음\n거래 0건')
})

it('sums recent transaction counts for marker cluster badges', () => {
  expect(sumRecentTransactionCount([
    property(1001, 37.5, 127.03),
    { ...property(1002, 37.51, 127.04), recentTransactionCount: 4 },
    { ...property(1003, 37.52, 127.05), recentTransactionCount: 2 }
  ])).toBe(7)
})
```

Update the `property` helper in `kakaoMap.test.ts` to include:

```ts
recentTransactionCount: 1,
```

- [ ] **Step 2: Run the helper test to verify it fails**

Run:

```powershell
cd frontend
npm test -- src/map/__tests__/kakaoMap.test.ts
```

Expected: FAIL because the new exports and `recentTransactionCount` type do not exist.

- [ ] **Step 3: Add frontend types and pure helpers**

In `frontend/src/api/types.ts`, add:

```ts
export type AdministrativeClusterLevel = 'LEGAL_DONG' | 'SIGUNGU'

export interface AdministrativeCluster {
  clusterId: string
  level: AdministrativeClusterLevel
  sido: string
  sigungu: string
  legalDong?: string | null
  label: string
  centerLat: number
  centerLng: number
  propertyCount: number
  transactionCount: number
  averageDealAmount?: number | null
}
```

Add `recentTransactionCount` to `PropertyMapItem`:

```ts
recentTransactionCount: number
```

Add `administrativeClusters` to `PropertyMapResponse`:

```ts
administrativeClusters: AdministrativeCluster[]
```

In `frontend/src/map/kakaoMap.ts`, update the import:

```ts
import type { AdministrativeCluster, Bounds, PropertyMapItem } from '@/api/types'
import { formatKrw } from '@/utils/format'
```

Add these helpers:

```ts
export interface MapMarkerRenderMode {
  showIndividualMarkers: boolean
  showTransactionClusterer: boolean
  showAdministrativeClusters: boolean
}

export function mapMarkerRenderMode(zoomLevel: number): MapMarkerRenderMode {
  return {
    showIndividualMarkers: zoomLevel <= 3,
    showTransactionClusterer: zoomLevel >= 4,
    showAdministrativeClusters: zoomLevel >= 5
  }
}

export function sumRecentTransactionCount(items: PropertyMapItem[]): number {
  return items.reduce((total, item) => total + (item.recentTransactionCount ?? 0), 0)
}

export function formatAdministrativeClusterLabel(cluster: AdministrativeCluster): string {
  const averageText =
    typeof cluster.averageDealAmount === 'number'
      ? `평균 ${formatKrw(cluster.averageDealAmount)}`
      : '평균 정보 없음'
  return `${cluster.label}\n${averageText}\n거래 ${cluster.transactionCount.toLocaleString('ko-KR')}건`
}
```

- [ ] **Step 4: Run the helper test to verify it passes**

Run:

```powershell
cd frontend
npm test -- src/map/__tests__/kakaoMap.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit frontend types and pure helpers**

Run:

```powershell
git status --short
git add frontend/src/api/types.ts `
  frontend/src/map/kakaoMap.ts `
  frontend/src/map/__tests__/kakaoMap.test.ts
git commit -m "feat: add map cluster render helpers"
```

---

### Task 4: Kakao MarkerClusterer And Administrative Overlay Utilities

**Files:**
- Modify: `frontend/src/map/kakaoMap.ts`
- Test: `frontend/src/map/__tests__/kakaoMap.test.ts`

- [ ] **Step 1: Write failing map utility tests**

Add this test to `kakaoMap.test.ts`:

```ts
it('creates transaction clusterer markers and updates cluster badges with recent transaction totals', () => {
  const clusterMarker = { setContent: vi.fn() }
  const clusters: Array<{ getMarkers: () => unknown[]; getClusterMarker: () => typeof clusterMarker }> = []
  const map = { id: 'map' }
  const kakaoMaps = {
    LatLng: vi.fn((lat: number, lng: number) => ({ lat, lng })),
    Marker: vi.fn(() => ({ setMap: vi.fn() })),
    MarkerClusterer: vi.fn(() => ({
      addMarkers: vi.fn((markers: unknown[]) => {
        clusters.push({
          getMarkers: () => markers,
          getClusterMarker: () => clusterMarker
        })
      }),
      clear: vi.fn(),
      setMap: vi.fn()
    })),
    event: {
      addListener: vi.fn((_target: unknown, eventName: string, handler: (clusters: unknown[]) => void) => {
        if (eventName === 'clustered') {
          handler(clusters)
        }
      })
    }
  }

  const clusterer = syncKakaoTransactionClusters({
    kakaoMaps,
    map,
    previousClusterer: null,
    items: [
      { ...property(1001, 37.5, 127.03), recentTransactionCount: 3 },
      { ...property(1002, 37.51, 127.04), recentTransactionCount: 5 }
    ]
  })

  expect(clusterer).not.toBeNull()
  expect(kakaoMaps.MarkerClusterer).toHaveBeenCalledWith(expect.objectContaining({
    map,
    averageCenter: true,
    minLevel: 4
  }))
  expect(clusterMarker.setContent).toHaveBeenCalledWith(expect.stringContaining('거래 8건'))
})

it('renders administrative cluster overlays and clears previous overlays', () => {
  const oldOverlay = { setMap: vi.fn() }
  const map = { id: 'map' }
  const overlays: Array<{ setMap: ReturnType<typeof vi.fn>; content: string }> = []
  const kakaoMaps = {
    LatLng: vi.fn((lat: number, lng: number) => ({ lat, lng })),
    CustomOverlay: vi.fn((options: { content: string }) => {
      const overlay = {
        setMap: vi.fn(),
        content: options.content
      }
      overlays.push(overlay)
      return overlay
    })
  }

  const nextOverlays = syncAdministrativeClusterOverlays({
    kakaoMaps,
    map,
    previousOverlays: [oldOverlay],
    clusters: [{
      clusterId: 'LEGAL_DONG:서울특별시:종로구:무악동',
      level: 'LEGAL_DONG',
      sido: '서울특별시',
      sigungu: '종로구',
      legalDong: '무악동',
      label: '무악동',
      centerLat: 37.5738636,
      centerLng: 126.9594466,
      propertyCount: 12,
      transactionCount: 31,
      averageDealAmount: 1080000000
    }]
  })

  expect(oldOverlay.setMap).toHaveBeenCalledWith(null)
  expect(nextOverlays).toHaveLength(1)
  expect(overlays[0].content).toContain('무악동')
  expect(overlays[0].content).toContain('평균 10.8억원')
})
```

Add these imports:

```ts
import {
  syncAdministrativeClusterOverlays,
  syncKakaoTransactionClusters
} from '@/map/kakaoMap'
```

- [ ] **Step 2: Run the map utility test to verify it fails**

Run:

```powershell
cd frontend
npm test -- src/map/__tests__/kakaoMap.test.ts
```

Expected: FAIL because `syncKakaoTransactionClusters` and `syncAdministrativeClusterOverlays` do not exist.

- [ ] **Step 3: Add Kakao utility implementations**

In `kakaoMap.ts`, add interfaces:

```ts
export interface KakaoOverlayLike {
  setMap(map: KakaoMapLike | null): void
}

export interface KakaoClusterLike {
  getMarkers(): KakaoMarkerLike[]
  getClusterMarker(): { setContent?(content: string): void }
}

export interface KakaoMarkerClustererLike {
  addMarkers(markers: KakaoMarkerLike[]): void
  clear(): void
  setMap?(map: KakaoMapLike | null): void
}
```

Extend `KakaoMapsApi`:

```ts
MarkerClusterer?: new (options: {
  map: KakaoMapLike
  averageCenter?: boolean
  minLevel?: number
  gridSize?: number
  styles?: Array<Record<string, string | number>>
}) => KakaoMarkerClustererLike
CustomOverlay?: new (options: {
  map?: KakaoMapLike
  position: unknown
  content: string
  yAnchor?: number
  xAnchor?: number
  zIndex?: number
}) => KakaoOverlayLike
```

Update the event signature:

```ts
event: {
  addListener(target: unknown, eventName: string, handler: (...args: unknown[]) => void): void
}
```

Add these helpers:

```ts
type TransactionClusterMarker = KakaoMarkerLike & {
  recentTransactionCount?: number
}

export function clearOverlayMarkers(markers: KakaoOverlayLike[]) {
  markers.forEach((marker) => marker.setMap(null))
}

export function clearKakaoTransactionClusterer(clusterer: KakaoMarkerClustererLike | null) {
  if (!clusterer) {
    return
  }
  clusterer.clear()
  clusterer.setMap?.(null)
}

function transactionClusterContent(count: number): string {
  return `<div class="map-transaction-cluster">거래 ${count.toLocaleString('ko-KR')}건</div>`
}

function administrativeClusterContent(cluster: AdministrativeCluster): string {
  const [label, average, count] = formatAdministrativeClusterLabel(cluster).split('\n')
  return `<div class="map-admin-cluster" data-cluster-id="${cluster.clusterId}">
    <strong>${label}</strong>
    <span>${average}</span>
    <small>${count}</small>
  </div>`
}

export function syncKakaoTransactionClusters(options: {
  kakaoMaps: KakaoMapsApi
  map: KakaoMapLike
  previousClusterer: KakaoMarkerClustererLike | null
  items: PropertyMapItem[]
}): KakaoMarkerClustererLike | null {
  clearKakaoTransactionClusterer(options.previousClusterer)
  if (!options.kakaoMaps.MarkerClusterer) {
    return null
  }

  const markers = options.items.map((item) => {
    const marker = new options.kakaoMaps.Marker({
      map: options.map,
      position: new options.kakaoMaps.LatLng(item.lat, item.lng),
      title: `property-${item.propertyId}`,
      clickable: false
    }) as TransactionClusterMarker
    marker.recentTransactionCount = item.recentTransactionCount ?? 0
    return marker
  })

  const clusterer = new options.kakaoMaps.MarkerClusterer({
    map: options.map,
    averageCenter: true,
    minLevel: 4,
    gridSize: 72
  })
  clusterer.addMarkers(markers)
  options.kakaoMaps.event.addListener(clusterer, 'clustered', (clusters) => {
    ;(clusters as KakaoClusterLike[]).forEach((cluster) => {
      const count = cluster.getMarkers().reduce((total, marker) => {
        return total + ((marker as TransactionClusterMarker).recentTransactionCount ?? 0)
      }, 0)
      cluster.getClusterMarker().setContent?.(transactionClusterContent(count))
    })
  })
  return clusterer
}

export function syncAdministrativeClusterOverlays(options: {
  kakaoMaps: KakaoMapsApi
  map: KakaoMapLike
  previousOverlays: KakaoOverlayLike[]
  clusters: AdministrativeCluster[]
}): KakaoOverlayLike[] {
  clearOverlayMarkers(options.previousOverlays)
  if (!options.kakaoMaps.CustomOverlay) {
    return []
  }
  return options.clusters.map((cluster) => new options.kakaoMaps.CustomOverlay!({
    map: options.map,
    position: new options.kakaoMaps.LatLng(cluster.centerLat, cluster.centerLng),
    content: administrativeClusterContent(cluster),
    xAnchor: 0.5,
    yAnchor: 1,
    zIndex: 3
  }))
}
```

- [ ] **Step 4: Run the map utility test to verify it passes**

Run:

```powershell
cd frontend
npm test -- src/map/__tests__/kakaoMap.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit Kakao map utility layers**

Run:

```powershell
git status --short
git add frontend/src/map/kakaoMap.ts frontend/src/map/__tests__/kakaoMap.test.ts
git commit -m "feat: add kakao map cluster layers"
```

---

### Task 5: KakaoMapPanel Layer Integration

**Files:**
- Modify: `frontend/src/components/KakaoMapPanel.vue`
- Modify: `frontend/src/styles/base.css`
- Test: `frontend/src/components/__tests__/KakaoMapPanel.test.ts`

- [ ] **Step 1: Write failing component tests**

In `KakaoMapPanel.test.ts`, extend the mock state:

```ts
clusterers: [] as Array<{ addMarkers: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn>; setMap: ReturnType<typeof vi.fn> }>,
overlays: [] as Array<{ setMap: ReturnType<typeof vi.fn>; content: string }>
```

Extend mock `maps`:

```ts
MarkerClusterer: vi.fn(() => {
  const clusterer = {
    addMarkers: vi.fn(),
    clear: vi.fn(),
    setMap: vi.fn()
  }
  state.clusterers.push(clusterer)
  return clusterer
}),
CustomOverlay: vi.fn((options: { content: string }) => {
  const overlay = {
    setMap: vi.fn(),
    content: options.content
  }
  state.overlays.push(overlay)
  return overlay
}),
```

Reset the new mocks in `beforeEach`:

```ts
kakaoMock.state.clusterers = []
kakaoMock.state.overlays = []
kakaoMock.maps.MarkerClusterer.mockClear()
kakaoMock.maps.CustomOverlay.mockClear()
```

Update the existing "creates a Kakao map..." test to force level 3 before mount:

```ts
kakaoMock.state.mapInstance.getLevel.mockReturnValue(3)
```

Add this test:

```ts
it('shows markerclusterer from level four and administrative overlays from level five', async () => {
  kakaoMock.state.hasKey = true
  kakaoMock.state.mapInstance.getLevel.mockReturnValue(5)

  mount(KakaoMapPanel, {
    props: {
      items: [{ ...property(1001), recentTransactionCount: 6 }],
      administrativeClusters: [{
        clusterId: 'LEGAL_DONG:서울특별시:종로구:무악동',
        level: 'LEGAL_DONG',
        sido: '서울특별시',
        sigungu: '종로구',
        legalDong: '무악동',
        label: '무악동',
        centerLat: 37.5738636,
        centerLng: 126.9594466,
        propertyCount: 12,
        transactionCount: 31,
        averageDealAmount: 1080000000
      }]
    }
  })
  await flushPromises()

  expect(kakaoMock.maps.MarkerClusterer).toHaveBeenCalledTimes(1)
  expect(kakaoMock.maps.CustomOverlay).toHaveBeenCalledTimes(1)
  expect(kakaoMock.state.overlays[0].content).toContain('무악동')
  expect(kakaoMock.state.overlays[0].content).toContain('거래 31건')
})

it('keeps markerclusterer active at level seven with sigungu overlays', async () => {
  kakaoMock.state.hasKey = true
  kakaoMock.state.mapInstance.getLevel.mockReturnValue(7)

  mount(KakaoMapPanel, {
    props: {
      items: [{ ...property(1001), recentTransactionCount: 3 }],
      administrativeClusters: [{
        clusterId: 'SIGUNGU:서울특별시:종로구',
        level: 'SIGUNGU',
        sido: '서울특별시',
        sigungu: '종로구',
        legalDong: null,
        label: '종로구',
        centerLat: 37.5738636,
        centerLng: 126.9594466,
        propertyCount: 42,
        transactionCount: 88,
        averageDealAmount: 965000000
      }]
    }
  })
  await flushPromises()

  expect(kakaoMock.maps.MarkerClusterer).toHaveBeenCalledTimes(1)
  expect(kakaoMock.maps.CustomOverlay).toHaveBeenCalledTimes(1)
  expect(kakaoMock.state.overlays[0].content).toContain('종로구')
})
```

Update the `property` helper in `KakaoMapPanel.test.ts`:

```ts
recentTransactionCount: 1,
```

- [ ] **Step 2: Run component tests to verify they fail**

Run:

```powershell
cd frontend
npm test -- src/components/__tests__/KakaoMapPanel.test.ts
```

Expected: FAIL because `administrativeClusters` prop and map layer integration do not exist.

- [ ] **Step 3: Integrate map layers in KakaoMapPanel**

Update imports in `KakaoMapPanel.vue`:

```ts
import type { AdministrativeCluster, PropertyMapItem } from '@/api/types'
import {
  clearKakaoTransactionClusterer,
  clearMarkers,
  clearOverlayMarkers,
  mapMarkerRenderMode,
  syncAdministrativeClusterOverlays,
  syncKakaoMarkers,
  syncKakaoTransactionClusters,
  viewportFromMap,
  type LatLngPoint,
  type KakaoMapLike,
  type KakaoMapsApi,
  type KakaoMarkerClustererLike,
  type KakaoMarkerLike,
  type KakaoOverlayLike,
  type MapViewport
} from '@/map/kakaoMap'
```

Add prop:

```ts
administrativeClusters: AdministrativeCluster[]
```

Add default:

```ts
administrativeClusters: () => []
```

Add layer state:

```ts
let transactionClusterer: KakaoMarkerClustererLike | null = null
let administrativeOverlays: KakaoOverlayLike[] = []
```

Replace `renderMarkers` with:

```ts
function renderMapLayers() {
  if (!kakaoMaps || !map) {
    return
  }

  const mode = mapMarkerRenderMode(map.getLevel())

  if (mode.showIndividualMarkers) {
    clearKakaoTransactionClusterer(transactionClusterer)
    transactionClusterer = null
    clearOverlayMarkers(administrativeOverlays)
    administrativeOverlays = []
    markers = syncKakaoMarkers({
      kakaoMaps,
      map,
      previousMarkers: markers,
      items: props.items,
      selectedPropertyId: props.selectedPropertyId,
      onClick: (propertyId) => emit('propertySelected', propertyId)
    })
    return
  }

  clearMarkers(markers)
  markers = []

  transactionClusterer = syncKakaoTransactionClusters({
    kakaoMaps,
    map,
    previousClusterer: transactionClusterer,
    items: props.items
  })

  administrativeOverlays = mode.showAdministrativeClusters
    ? syncAdministrativeClusterOverlays({
        kakaoMaps,
        map,
        previousOverlays: administrativeOverlays,
        clusters: props.administrativeClusters
      })
    : []
}
```

Replace calls and watchers:

```ts
renderMapLayers()
watch(() => [props.items, props.selectedPropertyId, props.administrativeClusters], renderMapLayers, { deep: true })
```

Update `scheduleBoundsChanged` so layer mode responds to zoom changes immediately:

```ts
function scheduleBoundsChanged() {
  renderMapLayers()
  if (idleTimer) {
    window.clearTimeout(idleTimer)
  }

  idleTimer = window.setTimeout(() => emitViewport('boundsChanged'), 180)
}
```

Update `onBeforeUnmount`:

```ts
clearMarkers(markers)
clearKakaoTransactionClusterer(transactionClusterer)
clearOverlayMarkers(administrativeOverlays)
markers = []
transactionClusterer = null
administrativeOverlays = []
```

Add CSS to `base.css`:

```css
.map-admin-cluster,
.map-transaction-cluster {
  display: grid;
  place-items: center;
  border: 1px solid rgba(23, 32, 51, 0.16);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.18);
  font-weight: 800;
  text-align: center;
}

.map-admin-cluster {
  min-width: 116px;
  gap: 2px;
  padding: 9px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  color: #172033;
  font-size: 0.82rem;
}

.map-admin-cluster span,
.map-admin-cluster small {
  color: var(--text-muted);
  font-size: 0.76rem;
}

.map-transaction-cluster {
  min-width: 64px;
  min-height: 38px;
  padding: 7px 9px;
  border-radius: 999px;
  background: var(--brand);
  color: #ffffff;
  font-size: 0.76rem;
}
```

- [ ] **Step 4: Run component tests to verify they pass**

Run:

```powershell
cd frontend
npm test -- src/components/__tests__/KakaoMapPanel.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit KakaoMapPanel integration**

Run:

```powershell
git status --short
git add frontend/src/components/KakaoMapPanel.vue `
  frontend/src/components/__tests__/KakaoMapPanel.test.ts `
  frontend/src/styles/base.css
git commit -m "feat: render map cluster layers"
```

---

### Task 6: MapView API Integration

**Files:**
- Modify: `frontend/src/views/MapView.vue`
- Modify: `frontend/src/views/__tests__/mapAndDetailViews.test.ts`

- [ ] **Step 1: Write failing MapView integration test**

Update `mapResponse` in `mapAndDetailViews.test.ts`:

```ts
function mapResponse(items: PropertyMapItem[], administrativeClusters = []) {
  return {
    items,
    administrativeClusters,
    bounds: {
      swLat: 37.48,
      swLng: 127.01,
      neLat: 37.52,
      neLng: 127.06
    },
    filters: {
      propertyTypes: ['APARTMENT'],
      transactionTypes: ['SALE'],
      zoomLevel: 5
    }
  }
}
```

Add `recentTransactionCount: 1` to every `PropertyMapItem` fixture in this test file.

Mock `KakaoMapPanel` at the top of the file before mounting `MapView`:

```ts
vi.mock('@/components/KakaoMapPanel.vue', () => ({
  default: {
    name: 'KakaoMapPanel',
    props: ['items', 'administrativeClusters', 'selectedPropertyId', 'focusTarget', 'focusZoomLevel'],
    emits: ['ready', 'boundsChanged', 'propertySelected', 'loadError'],
    template: '<section data-test="kakao-map-panel">{{ administrativeClusters.length }}</section>'
  }
}))
```

Add this test:

```ts
it('passes administrative clusters from map API response into the Kakao map panel', async () => {
  propertyApiMock.getMapProperties.mockResolvedValueOnce(mapResponse([seedMapItem], [{
    clusterId: 'LEGAL_DONG:서울특별시:종로구:무악동',
    level: 'LEGAL_DONG',
    sido: '서울특별시',
    sigungu: '종로구',
    legalDong: '무악동',
    label: '무악동',
    centerLat: 37.5738636,
    centerLng: 126.9594466,
    propertyCount: 12,
    transactionCount: 31,
    averageDealAmount: 1080000000
  }]))

  const { wrapper } = await mountMapView()

  expect(wrapper.get('[data-test="kakao-map-panel"]').text()).toBe('1')
})
```

- [ ] **Step 2: Run MapView tests to verify they fail**

Run:

```powershell
cd frontend
npm test -- src/views/__tests__/mapAndDetailViews.test.ts
```

Expected: FAIL because `MapView` does not store or pass `administrativeClusters`.

- [ ] **Step 3: Store and pass administrative clusters in MapView**

In `MapView.vue`, update imports:

```ts
import type {
  AdministrativeCluster,
  FavoriteAreaCreateRequest,
  PropertyMapItem,
  PropertySearchItem,
  PropertyType,
  TransactionType
} from '@/api/types'
```

Add state:

```ts
const administrativeClusters = ref<AdministrativeCluster[]>([])
```

In `searchVisibleArea`, after the API response:

```ts
items.value = response.items
administrativeClusters.value = response.administrativeClusters
```

In catch blocks for map and keyword searches, clear clusters:

```ts
administrativeClusters.value = []
```

In `searchByKeyword`, clear clusters because keyword search focuses a result list:

```ts
administrativeClusters.value = []
```

Pass the prop:

```vue
<KakaoMapPanel
  :items="items"
  :administrative-clusters="administrativeClusters"
  :selected-property-id="selectedPropertyId"
  :focus-target="mapFocusTarget"
  :focus-zoom-level="zoomLevel"
  @ready="handleMapReady"
  @bounds-changed="handleBoundsChanged"
  @property-selected="selectProperty"
  @load-error="handleMapLoadError"
/>
```

- [ ] **Step 4: Run MapView tests to verify they pass**

Run:

```powershell
cd frontend
npm test -- src/views/__tests__/mapAndDetailViews.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit MapView integration**

Run:

```powershell
git status --short
git add frontend/src/views/MapView.vue frontend/src/views/__tests__/mapAndDetailViews.test.ts
git commit -m "feat: pass map administrative clusters"
```

---

### Task 7: Contract Documentation And Full Verification

**Files:**
- Modify: `docs/contracts/property-api.md`

- [ ] **Step 1: Update the property API contract**

In `docs/contracts/property-api.md`, update the map response example so each item includes:

```json
"dealCount": 18,
"recentTransactionCount": 6,
"aiAvailable": true
```

Add `administrativeClusters` between `items` and `bounds`:

```json
"administrativeClusters": [
  {
    "clusterId": "LEGAL_DONG:서울특별시:종로구:무악동",
    "level": "LEGAL_DONG",
    "sido": "서울특별시",
    "sigungu": "종로구",
    "legalDong": "무악동",
    "label": "무악동",
    "centerLat": 37.5738636,
    "centerLng": 126.9594466,
    "propertyCount": 12,
    "transactionCount": 31,
    "averageDealAmount": 1080000000
  }
],
```

Add this paragraph after the response example:

```md
`recentTransactionCount` counts transactions from the last 6 months that match the current map filters. `administrativeClusters` is empty for zoom levels 1-4, contains `LEGAL_DONG` clusters for zoom levels 5-6, and contains `SIGUNGU` clusters for zoom level 7 and above. Cluster averages use `COALESCE(deal_amount_krw, deposit_amount_krw)` and return `averageDealAmount: null` when no matching recent transaction has a price-like amount.
```

- [ ] **Step 2: Run targeted verification**

Run backend tests:

```powershell
cd backend
mvn -Dtest=PropertyServiceTest,PropertyMapperMyBatisTest test
```

Expected: PASS.

Run frontend tests:

```powershell
cd frontend
npm test -- src/map/__tests__/kakaoMap.test.ts src/components/__tests__/KakaoMapPanel.test.ts src/views/__tests__/mapAndDetailViews.test.ts
```

Expected: PASS.

- [ ] **Step 3: Run type and build verification**

Run:

```powershell
cd frontend
npm run typecheck
npm run build-only
```

Expected: both commands PASS.

Run:

```powershell
cd backend
mvn test
```

Expected: PASS.

- [ ] **Step 4: Commit documentation and verification-ready state**

Run:

```powershell
git status --short
git add docs/contracts/property-api.md
git commit -m "docs: document map cluster contract"
```

- [ ] **Step 5: Final handoff note**

Include these items in the implementation final response:

```text
Changed paths:
- backend/src/main/java/com/jiber/backend/property/AdministrativeClusterLevel.java
- backend/src/main/java/com/jiber/backend/property/AdministrativeClusterResponse.java
- backend/src/main/java/com/jiber/backend/property/AdministrativeClusterRow.java
- backend/src/main/java/com/jiber/backend/property/PropertyMapItemResponse.java
- backend/src/main/java/com/jiber/backend/property/PropertyMapResponse.java
- backend/src/main/java/com/jiber/backend/property/PropertyListRow.java
- backend/src/main/java/com/jiber/backend/property/PropertyMapper.java
- backend/src/main/java/com/jiber/backend/property/PropertyService.java
- backend/src/main/resources/mapper/PropertyMapper.xml
- backend/src/test/java/com/jiber/backend/property/PropertyServiceTest.java
- backend/src/test/java/com/jiber/backend/property/PropertyMapperMyBatisTest.java
- frontend/src/api/types.ts
- frontend/src/map/kakaoMap.ts
- frontend/src/map/__tests__/kakaoMap.test.ts
- frontend/src/components/KakaoMapPanel.vue
- frontend/src/components/__tests__/KakaoMapPanel.test.ts
- frontend/src/styles/base.css
- frontend/src/views/MapView.vue
- frontend/src/views/__tests__/mapAndDetailViews.test.ts
- docs/contracts/property-api.md

Verification:
- cd backend; mvn -Dtest=PropertyServiceTest,PropertyMapperMyBatisTest test
- cd backend; mvn test
- cd frontend; npm test -- src/map/__tests__/kakaoMap.test.ts src/components/__tests__/KakaoMapPanel.test.ts src/views/__tests__/mapAndDetailViews.test.ts
- cd frontend; npm run typecheck
- cd frontend; npm run build-only

Contract changes:
- GET /api/v1/properties/map items now include recentTransactionCount.
- GET /api/v1/properties/map now includes administrativeClusters.

Blockers:
- None

Next agent notes:
- Frontend Map Agent should visually QA map levels 3, 4, 5, 6, and 7 with a real Kakao key.
- Backend API Agent should review MySQL query performance after realistic public-data volume is loaded.
```

---

## Self-Review Checklist

- Spec coverage: The plan covers MarkerClusterer at level 4+, administrative legal-dong clusters at levels 5-6, administrative sigungu clusters at level 7+, recent 6-month transaction counts, average prices, API contract updates, Korean display text, tests, and docs.
- Type consistency: Backend uses `recentTransactionCount`, `administrativeClusters`, `AdministrativeClusterLevel.LEGAL_DONG`, and `AdministrativeClusterLevel.SIGUNGU`. Frontend uses `recentTransactionCount`, `AdministrativeCluster`, `LEGAL_DONG`, and `SIGUNGU`.
- Dependency order: Backend contract lands before frontend API consumption. Pure frontend helpers land before component integration. Contract docs land after implementation details are settled.
