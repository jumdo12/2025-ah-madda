package com.ahmadda.domain.event;

import com.ahmadda.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventApplicationForm extends BaseEntity {

    private static final int INITIAL_REVISION_NUMBER = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_form_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true, updatable = false)
    private Event event;

    @Column(nullable = false)
    private int activeRevisionNumber;

    @OneToMany(mappedBy = "applicationForm", cascade = CascadeType.ALL)
    @BatchSize(size = 16)
    private final List<ApplicationFormRevision> revisions = new ArrayList<>();

    private EventApplicationForm(final Event event, final List<Question> questions) {
        this.event = event;
        this.activeRevisionNumber = INITIAL_REVISION_NUMBER;
        this.revisions.add(ApplicationFormRevision.create(
                this,
                INITIAL_REVISION_NUMBER,
                questions
        ));
    }

    public static EventApplicationForm create(final Event event, final List<Question> questions) {
        return new EventApplicationForm(event, questions);
    }

    public ApplicationFormRevision revise(final List<Question> questions) {
        int nextRevisionNumber = activeRevisionNumber + 1;
        ApplicationFormRevision revision = ApplicationFormRevision.create(
                this,
                nextRevisionNumber,
                questions
        );

        revisions.add(revision);
        activeRevisionNumber = nextRevisionNumber;

        return revision;
    }

    public ApplicationFormRevision getActiveRevision() {
        return revisions.stream()
                .filter(revision -> revision.hasRevisionNumber(activeRevisionNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("활성 신청서 버전이 존재하지 않습니다."));
    }

    public List<Question> getActiveQuestions() {
        return getActiveRevision().getQuestions();
    }
}
