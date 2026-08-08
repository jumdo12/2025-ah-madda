package com.ahmadda.application.dto;

import com.ahmadda.domain.event.Event;

public record EventCapacityUpdated(
        Long eventId,
        int maxCapacity
) {

    public static EventCapacityUpdated from(final Event event) {
        return new EventCapacityUpdated(event.getId(), event.getMaxCapacity());
    }
}
