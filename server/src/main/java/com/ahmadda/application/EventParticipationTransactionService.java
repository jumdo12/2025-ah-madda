package com.ahmadda.application;

import com.ahmadda.application.dto.AnswerCreateRequest;
import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.common.exception.NotFoundException;
import com.ahmadda.domain.event.ApplicationFormVersion;
import com.ahmadda.domain.event.ApplicationFormVersionRepository;
import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.event.EventRepository;
import com.ahmadda.domain.event.Guest;
import com.ahmadda.domain.event.GuestRepository;
import com.ahmadda.domain.event.Question;
import com.ahmadda.domain.event.QuestionRepository;
import com.ahmadda.domain.organization.Organization;
import com.ahmadda.domain.organization.OrganizationMember;
import com.ahmadda.domain.organization.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventParticipationTransactionService {

    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final QuestionRepository questionRepository;
    private final ApplicationFormVersionRepository applicationFormVersionRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public void participate(
            final UUID participationRequestId,
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        if (guestRepository.countByParticipationRequestIdIncludingDeleted(participationRequestId) > 0) {
            return;
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));

        saveParticipation(
                participationRequestId,
                event,
                loginMember,
                currentDateTime,
                eventParticipateRequest
        );
    }

    @Transactional
    public void participateWithPessimisticLock(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));

        saveParticipation(
                UUID.randomUUID(),
                event,
                loginMember,
                currentDateTime,
                eventParticipateRequest
        );
    }

    private void saveParticipation(
            final UUID participationRequestId,
            final Event event,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        Organization organization = event.getOrganization();
        ApplicationFormVersion applicationFormVersion = getApplicationFormVersion(
                eventParticipateRequest.applicationFormVersionId()
        );
        OrganizationMember organizationMember = organizationMemberRepository.findByOrganizationIdAndMemberId(
                        organization.getId(),
                        loginMember.memberId()
                )
                .orElseThrow(() -> new NotFoundException("존재하지 않는 구성원입니다."));

        Guest guest = Guest.create(
                participationRequestId,
                event,
                organizationMember,
                applicationFormVersion,
                currentDateTime
        );

        Map<Question, String> questionAnswers = getQuestionAnswers(eventParticipateRequest.answers());
        guest.submitAnswers(questionAnswers);

        guestRepository.save(guest);
    }

    @Transactional
    public void cancel(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));
        OrganizationMember organizationMember = organizationMemberRepository.findByOrganizationIdAndMemberId(
                        event.getOrganization().getId(),
                        loginMember.memberId()
                )
                .orElseThrow(() -> new NotFoundException("존재하지 않는 구성원입니다."));

        event.cancelParticipation(organizationMember, currentDateTime);
        guestRepository.deleteByEventAndOrganizationMember(event, organizationMember);
    }

    private Map<Question, String> getQuestionAnswers(final List<AnswerCreateRequest> answerCreateRequests) {
        return answerCreateRequests.stream()
                .map(answerRequest -> Map.entry(getQuestion(answerRequest.questionId()), answerRequest.answerText()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Question getQuestion(final Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 질문입니다."));
    }

    private ApplicationFormVersion getApplicationFormVersion(final Long applicationFormVersionId) {
        return applicationFormVersionRepository.findById(applicationFormVersionId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 신청서 버전입니다."));
    }
}
