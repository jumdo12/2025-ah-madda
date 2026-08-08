package com.ahmadda.application.dto;

import com.ahmadda.common.exception.ServiceUnavailableException;

public enum SeatClaimResult {
    ACQUIRED(1L),
    SOLD_OUT(0L),
    NOT_INITIALIZED(-1L),
    ALREADY_ACQUIRED(-2L),
    BYPASSED(2L);

    private final long code;

    SeatClaimResult(final long code) {
        this.code = code;
    }

    public static SeatClaimResult fromCode(final Long code) {
        if (code == null) {
            throw new ServiceUnavailableException("이벤트 잔여석 저장소에서 응답을 받지 못했습니다.");
        }

        for (SeatClaimResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }

        throw new ServiceUnavailableException("이벤트 잔여석 저장소에서 알 수 없는 응답을 받았습니다.");
    }
}
