package com.jiber.backend.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    PROPERTY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 부동산 정보를 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 공지사항을 찾을 수 없습니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 아파트 정보를 찾을 수 없습니다."),
    FAVORITE_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 지역 정보를 찾을 수 없습니다."),
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 관심 아파트에 추가된 항목입니다."),
    FAVORITE_AREA_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 관심 지역에 추가된 항목입니다."),
    VALUATION_UNSUPPORTED_PROPERTY_TYPE(HttpStatus.UNPROCESSABLE_ENTITY, "아파트 단지에 한해 제공되는 기능입니다."),
    VALUATION_INSUFFICIENT_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "가격 추정에 필요한 데이터가 부족합니다."),
    MODEL_SERVER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "가격 추정 서버와 연결할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
