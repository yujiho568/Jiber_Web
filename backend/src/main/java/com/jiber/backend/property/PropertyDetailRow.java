package com.jiber.backend.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PropertyDetailRow {

    private Long propertyId;
    private PropertyType propertyType;
    private String name;
    private String sido;
    private String sigungu;
    private String legalDong;
    private String roadAddress;
    private String jibunAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer builtYear;
    private Integer householdCount;
    private Long latestDealAmount;
    private LocalDate latestDealDate;

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getRoadAddress() {
        return roadAddress;
    }

    public void setRoadAddress(String roadAddress) {
        this.roadAddress = roadAddress;
    }

    public String getJibunAddress() {
        return jibunAddress;
    }

    public void setJibunAddress(String jibunAddress) {
        this.jibunAddress = jibunAddress;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Integer getBuiltYear() {
        return builtYear;
    }

    public void setBuiltYear(Integer builtYear) {
        this.builtYear = builtYear;
    }

    public Integer getHouseholdCount() {
        return householdCount;
    }

    public void setHouseholdCount(Integer householdCount) {
        this.householdCount = householdCount;
    }

    public Long getLatestDealAmount() {
        return latestDealAmount;
    }

    public void setLatestDealAmount(Long latestDealAmount) {
        this.latestDealAmount = latestDealAmount;
    }

    public LocalDate getLatestDealDate() {
        return latestDealDate;
    }

    public void setLatestDealDate(LocalDate latestDealDate) {
        this.latestDealDate = latestDealDate;
    }
}
