package com.jiber.backend.favorite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class FavoriteAreaRow {

    private Long favoriteAreaId;
    private String label;
    private String sido;
    private String sigungu;
    private String legalDong;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
    private Integer zoomLevel;
    private String normalizedKey;
    private OffsetDateTime createdAt;

    public Long getFavoriteAreaId() {
        return favoriteAreaId;
    }

    public void setFavoriteAreaId(Long favoriteAreaId) {
        this.favoriteAreaId = favoriteAreaId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public Integer getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(Integer zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    public String getNormalizedKey() {
        return normalizedKey;
    }

    public void setNormalizedKey(String normalizedKey) {
        this.normalizedKey = normalizedKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
