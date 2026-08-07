package com.ahmadda.application.listener;

import com.ahmadda.application.EventSeatInventory;
import com.ahmadda.application.dto.EventCapacityUpdated;
import com.ahmadda.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSeatInventoryUpdatedListener {

    private final EventSeatInventory eventSeatInventory;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCapacityUpdated(final EventCapacityUpdated eventCapacityUpdated) {
        try {
            eventSeatInventory.synchronizeCapacity(
                    eventCapacityUpdated.eventId(),
                    eventCapacityUpdated.maxCapacity()
            );
        } catch (ServiceUnavailableException exception) {
            log.error("이벤트 정원 Redis 동기화 실패. eventId={}", eventCapacityUpdated.eventId(), exception);
        }
    }
}
