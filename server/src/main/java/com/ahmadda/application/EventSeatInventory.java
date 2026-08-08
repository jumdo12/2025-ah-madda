package com.ahmadda.application;

import com.ahmadda.application.dto.SeatClaimResult;

public interface EventSeatInventory {

    SeatClaimResult claim(Long eventId, Long memberId);

    void initialize(Long eventId, int maxCapacity, boolean approvalRequired);

    void release(Long eventId, Long memberId);

    void synchronizeCapacity(Long eventId, int maxCapacity);
}
