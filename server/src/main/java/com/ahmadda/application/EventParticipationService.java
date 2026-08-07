package com.ahmadda.application;

import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.application.dto.SeatClaimResult;
import com.ahmadda.common.exception.ServiceUnavailableException;
import com.ahmadda.common.exception.UnprocessableEntityException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventParticipationService {

    private final EventSeatInventory eventSeatInventory;
    private final EventParticipationTransactionService eventParticipationTransactionService;

    public void participate(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        SeatClaimResult claimResult = eventSeatInventory.claim(eventId, loginMember.memberId());
        validateClaimResult(claimResult);

        try {
            eventParticipationTransactionService.participate(
                    eventId,
                    loginMember,
                    currentDateTime,
                    eventParticipateRequest
            );
        } catch (RuntimeException exception) {
            // 좌석 선점 보상 정책이 확정되면 이 지점에서 처리한다.
            throw exception;
        }
    }

    private void validateClaimResult(final SeatClaimResult claimResult) {
        switch (claimResult) {
            case ACQUIRED, BYPASSED -> {
            }
            case SOLD_OUT -> throw new UnprocessableEntityException(
                    "수용 인원이 가득차 이벤트에 참여할 수 없습니다."
            );
            case NOT_INITIALIZED -> throw new ServiceUnavailableException(
                    "이벤트 잔여석 정보가 준비되지 않았습니다."
            );
            case ALREADY_ACQUIRED -> throw new UnprocessableEntityException(
                    "이미 해당 이벤트의 좌석을 선점했습니다."
            );
        }
    }
}
