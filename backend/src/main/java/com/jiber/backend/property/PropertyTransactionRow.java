package com.jiber.backend.property;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PropertyTransactionRow {

    private Long transactionId;
    private TransactionType transactionType;
    private BigDecimal exclusiveAreaM2;
    private Integer floor;
    private Long dealAmount;
    private Long depositAmount;
    private Long monthlyRent;
    private LocalDate dealDate;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getExclusiveAreaM2() {
        return exclusiveAreaM2;
    }

    public void setExclusiveAreaM2(BigDecimal exclusiveAreaM2) {
        this.exclusiveAreaM2 = exclusiveAreaM2;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public Long getDealAmount() {
        return dealAmount;
    }

    public void setDealAmount(Long dealAmount) {
        this.dealAmount = dealAmount;
    }

    public Long getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(Long depositAmount) {
        this.depositAmount = depositAmount;
    }

    public Long getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(Long monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public LocalDate getDealDate() {
        return dealDate;
    }

    public void setDealDate(LocalDate dealDate) {
        this.dealDate = dealDate;
    }
}
