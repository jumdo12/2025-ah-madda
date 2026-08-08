package com.ahmadda.application.listener;

import com.ahmadda.application.EventSeatInventory;
import com.ahmadda.application.dto.EventCreated;
import com.ahmadda.common.exception.NotFoundException;
import com.ahmadda.common.exception.ServiceUnavailableException;
import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.event.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSeatInventoryCreatedListener {

    private final EventRepository eventRepository;
    private final EventSeatInventory eventSeatInventory;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCreated(final EventCreated eventCreated) {
        Event event = eventRepository.findById(eventCreated.eventId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));

        try {
            eventSeatInventory.initialize(
                    event.getId(),
                    event.getMaxCapacity(),
                    event.isApprovalRequired()
            );
        } catch (ServiceUnavailableException exception) {
            log.error("이벤트 잔여석 초기화 실패. eventId={}", eventCreated.eventId(), exception);
        }
    }
}
