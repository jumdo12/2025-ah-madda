package com.ahmadda.application;

import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.EventParticipationMessage;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.application.dto.SeatClaimResult;
import com.ahmadda.common.exception.ServiceUnavailableException;
import com.ahmadda.common.exception.UnprocessableEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventParticipationService {

    private final EventSeatInventory eventSeatInventory;
    private final EventParticipationTransactionService eventParticipationTransactionService;

    public void participate(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        UUID participationRequestId = UUID.randomUUID();
        EventParticipationMessage message = new EventParticipationMessage(
                participationRequestId,
                eventId,
                loginMember.memberId(),
                currentDateTime,
                eventParticipateRequest.answers()
        );
        SeatClaimResult claimResult = eventSeatInventory.claim(message);
        validateClaimResult(claimResult);
    }

    public void cancel(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime
    ) {
        eventParticipationTransactionService.cancel(eventId, loginMember, currentDateTime);
        releaseSafely(eventId, loginMember.memberId(), "예약 취소");
    }

    private void releaseSafely(final Long eventId, final Long memberId, final String reason) {
        try {
            eventSeatInventory.release(eventId, memberId);
        } catch (RuntimeException exception) {
            log.error("Redis 좌석 복구 실패. eventId={}, memberId={}, reason={}", eventId, memberId, reason, exception);
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
