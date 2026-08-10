package com.ahmadda.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApplicationFormUpdateRequest(
        @NotNull
        @Valid
        List<QuestionCreateRequest> questions
) {
}
