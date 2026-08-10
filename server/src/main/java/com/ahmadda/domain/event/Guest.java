package com.ahmadda.domain.event;


import com.ahmadda.common.exception.ForbiddenException;
import com.ahmadda.common.exception.UnprocessableEntityException;
import com.ahmadda.domain.BaseEntity;
import com.ahmadda.domain.organization.OrganizationMember;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE guest SET deleted_at = CURRENT_TIMESTAMP WHERE guest_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Guest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guest_id")
    private Long id;

    @Column(
            name = "participation_request_id",
            nullable = false,
            updatable = false,
            columnDefinition = "BINARY(16)"
    )
    private UUID participationRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private OrganizationMember organizationMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false, updatable = false)
    private ApplicationFormVersion applicationFormVersion;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "guest", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Answer> answers = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    private Guest(
            final UUID participationRequestId,
            final Event event,
            final OrganizationMember organizationMember,
            final ApplicationFormVersion applicationFormVersion,
            final LocalDateTime currentDateTime
    ) {
        validateSameOrganization(event, organizationMember);
        validateApplicationFormVersion(event, applicationFormVersion);

        this.participationRequestId = participationRequestId;
        this.event = event;
        this.organizationMember = organizationMember;
        this.applicationFormVersion = applicationFormVersion;
        this.approvalStatus = event.isApprovalRequired() ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED;

        event.participate(this, currentDateTime);
    }

    public static Guest create(
            final Event event,
            final OrganizationMember organizationMember,
            final LocalDateTime currentDateTime
    ) {
        return create(
                UUID.randomUUID(),
                event,
                organizationMember,
                event.getActiveApplicationFormVersion(),
                currentDateTime
        );
    }

    public static Guest create(
            final UUID participationRequestId,
            final Event event,
            final OrganizationMember organizationMember,
            final LocalDateTime currentDateTime
    ) {
        return create(
                participationRequestId,
                event,
                organizationMember,
                event.getActiveApplicationFormVersion(),
                currentDateTime
        );
    }

    public static Guest create(
            final UUID participationRequestId,
            final Event event,
            final OrganizationMember organizationMember,
            final ApplicationFormVersion applicationFormVersion,
            final LocalDateTime currentDateTime
    ) {
        return new Guest(
                participationRequestId,
                event,
                organizationMember,
                applicationFormVersion,
                currentDateTime
        );
    }

    public boolean isSameOrganizationMember(final OrganizationMember organizationMember) {
        return this.organizationMember.equals(organizationMember);
    }

    public void submitAnswers(final Map<Question, String> questionAnswers) {
        validateRequiredQuestions(questionAnswers);

        addAnswers(questionAnswers);
    }

    public List<Answer> viewAnswersAs(final OrganizationMember organizationMember) {
        if (!canViewAnswers(organizationMember)) {
            throw new ForbiddenException("답변을 볼 권한이 없습니다.");
        }

        return answers;
    }

    public void changeApprovalStatus(final ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    public boolean isBelongTo(final Event event) {
        return this.event.equals(event);
    }

    private void validateRequiredQuestions(final Map<Question, String> questionAnswers) {
        Set<Question> requiredQuestions = applicationFormVersion.getRequiredQuestions();

        for (Question required : requiredQuestions) {
            String answer = questionAnswers.get(required);
            if (answer == null || answer.isBlank()) {
                throw new UnprocessableEntityException("필수 질문에 대한 답변이 누락되었습니다.");
            }
        }
    }

    private void addAnswers(final Map<Question, String> answers) {
        answers.forEach((question, answerText) -> {
            if (!applicationFormVersion.hasQuestion(question)) {
                throw new UnprocessableEntityException("제출한 신청서 버전에 포함되지 않는 질문입니다.");
            }
            if (answerText == null || answerText.isBlank()) {
                return;
            }
            this.answers.add(Answer.create(question, this, answerText));
        });
    }

    private void validateSameOrganization(final Event event, final OrganizationMember organizationMember) {
        if (!organizationMember.isBelongTo(event.getOrganization())) {
            throw new UnprocessableEntityException("같은 이벤트 스페이스의 이벤트에만 게스트로 참여할 수 있습니다합니다.");
        }
    }

    private void validateApplicationFormVersion(
            final Event event,
            final ApplicationFormVersion applicationFormVersion
    ) {
        if (!applicationFormVersion.belongsTo(event)) {
            throw new UnprocessableEntityException("해당 이벤트의 신청서 버전이 아닙니다.");
        }
    }

    private boolean canViewAnswers(final OrganizationMember organizationMember) {
        return event.isOrganizer(organizationMember) || this.organizationMember.equals(organizationMember);
    }
}
