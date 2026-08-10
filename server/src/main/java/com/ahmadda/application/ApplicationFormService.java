package com.ahmadda.application;

import com.ahmadda.application.dto.ApplicationFormUpdateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.application.dto.QuestionCreateRequest;
import com.ahmadda.common.exception.NotFoundException;
import com.ahmadda.domain.event.ApplicationFormVersion;
import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.event.EventRepository;
import com.ahmadda.domain.event.Question;
import com.ahmadda.domain.member.Member;
import com.ahmadda.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationFormService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ApplicationFormVersion revise(
            final Long eventId,
            final LoginMember loginMember,
            final ApplicationFormUpdateRequest request
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다."));
        Member organizer = memberRepository.findById(loginMember.memberId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));

        return event.reviseApplicationForm(organizer, createQuestions(request.questions()));
    }

    private List<Question> createQuestions(final List<QuestionCreateRequest> requests) {
        List<Question> questions = new ArrayList<>(requests.size());

        for (int orderIndex = 0; orderIndex < requests.size(); orderIndex++) {
            QuestionCreateRequest request = requests.get(orderIndex);

            questions.add(Question.create(
                    request.questionText(),
                    request.isRequired(),
                    orderIndex
            ));
        }

        return questions;
    }
}
