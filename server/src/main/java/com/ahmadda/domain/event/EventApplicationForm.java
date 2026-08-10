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
import jakarta.persistence.Version;
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

    private static final int INITIAL_VERSION_NUMBER = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_form_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true, updatable = false)
    private Event event;

    @Column(nullable = false)
    private int activeVersionNumber;

    @Version
    @Column(nullable = false)
    private long lockVersion;

    @OneToMany(mappedBy = "applicationForm", cascade = CascadeType.ALL)
    @BatchSize(size = 16)
    private final List<ApplicationFormVersion> versions = new ArrayList<>();

    private EventApplicationForm(final Event event, final List<Question> questions) {
        this.event = event;
        this.activeVersionNumber = INITIAL_VERSION_NUMBER;
        this.versions.add(ApplicationFormVersion.create(
                this,
                INITIAL_VERSION_NUMBER,
                questions
        ));
    }

    public static EventApplicationForm create(final Event event, final List<Question> questions) {
        return new EventApplicationForm(event, questions);
    }

    public ApplicationFormVersion revise(final List<Question> questions) {
        int nextVersionNumber = activeVersionNumber + 1;
        ApplicationFormVersion version = ApplicationFormVersion.create(
                this,
                nextVersionNumber,
                questions
        );

        versions.add(version);
        activeVersionNumber = nextVersionNumber;

        return version;
    }

    public ApplicationFormVersion getActiveVersion() {
        return versions.stream()
                .filter(version -> version.hasVersionNumber(activeVersionNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("활성 신청서 버전이 존재하지 않습니다."));
    }

    boolean isFor(final Event event) {
        if (this.event == event) {
            return true;
        }
        if (this.event.getId() == null || event.getId() == null) {
            return false;
        }

        return this.event.getId()
                .equals(event.getId());
    }
}
