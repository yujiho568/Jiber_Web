package com.jiber.backend.favorite;

import com.jiber.backend.property.PropertyType;
import com.jiber.backend.property.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class FavoriteApartmentRow {

    private Long favoriteId;
    private Long propertyId;
    private PropertyType propertyType;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private TransactionType latestTransactionType;
    private Long latestDealAmount;
    private LocalDate latestDealDate;
    private OffsetDateTime createdAt;

    public Long getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(Long favoriteId) {
        this.favoriteId = favoriteId;
    }

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public TransactionType getLatestTransactionType() {
        return latestTransactionType;
    }

    public void setLatestTransactionType(TransactionType latestTransactionType) {
        this.latestTransactionType = latestTransactionType;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
