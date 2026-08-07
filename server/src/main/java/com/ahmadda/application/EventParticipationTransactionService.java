package com.ahmadda.application;

import com.ahmadda.application.dto.AnswerCreateRequest;
import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.common.exception.NotFoundException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventParticipationTransactionService {

    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final QuestionRepository questionRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public void participate(
            final Long eventId,
            final LoginMember loginMember,
            final LocalDateTime currentDateTime,
            final EventParticipateRequest eventParticipateRequest
    ) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));
        Organization organization = event.getOrganization();
        OrganizationMember organizationMember = organizationMemberRepository.findByOrganizationIdAndMemberId(
                        organization.getId(),
                        loginMember.memberId()
                )
                .orElseThrow(() -> new NotFoundException("존재하지 않는 구성원입니다."));

        Guest guest = Guest.create(event, organizationMember, currentDateTime);

        Map<Question, String> questionAnswers = getQuestionAnswers(eventParticipateRequest.answers());
        guest.submitAnswers(questionAnswers);

        guestRepository.save(guest);
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
}
