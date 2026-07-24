package com.ahmadda.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

public record EventUpdateRequest(
        @NotBlank
        String title,
        @Nullable
        String description,
        @Nullable
        String place,
        @NotNull
        LocalDateTime registrationEnd,
        @NotNull
        LocalDateTime eventStart,
        @NotNull
        LocalDateTime eventEnd,
        int maxCapacity,
        @Nullable
        @Positive
        Integer registrationClosingReminderMinutesBefore
) {

    public EventUpdateRequest(
            final String title,
            final String description,
            final String place,
            final LocalDateTime registrationEnd,
            final LocalDateTime eventStart,
            final LocalDateTime eventEnd,
            final int maxCapacity
    ) {
        this(
                title,
                description,
                place,
                registrationEnd,
                eventStart,
                eventEnd,
                maxCapacity,
                null
        );
    }

    public int registrationClosingReminderMinutesBeforeOr(final int currentMinutesBefore) {
        if (registrationClosingReminderMinutesBefore == null) {
            return currentMinutesBefore;
        }

        return registrationClosingReminderMinutesBefore;
    }
}
