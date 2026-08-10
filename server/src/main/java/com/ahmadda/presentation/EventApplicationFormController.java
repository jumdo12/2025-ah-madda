package com.ahmadda.presentation;

import com.ahmadda.application.ApplicationFormService;
import com.ahmadda.application.dto.ApplicationFormUpdateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.presentation.resolver.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Event Application Form", description = "이벤트 신청서 관련 API")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventApplicationFormController {

    private final ApplicationFormService applicationFormService;

    @Operation(summary = "이벤트 신청 질문 수정", description = "전체 신청 질문을 새로운 버전으로 교체합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "403"),
            @ApiResponse(responseCode = "404")
    })
    @PutMapping("/{eventId}/application-form")
    public ResponseEntity<Void> reviseApplicationForm(
            @PathVariable final Long eventId,
            @RequestBody @Valid final ApplicationFormUpdateRequest request,
            @Auth final LoginMember loginMember
    ) {
        applicationFormService.revise(eventId, loginMember, request);

        return ResponseEntity.noContent().build();
    }
}
