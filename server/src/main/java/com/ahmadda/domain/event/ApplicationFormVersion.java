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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "UK_application_form_version__form_version_number",
        columnNames = {"application_form_id", "version_number"}
))
public class ApplicationFormVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_version_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_form_id", nullable = false, updatable = false)
    private EventApplicationForm applicationForm;

    @Column(nullable = false, updatable = false)
    private int versionNumber;

    @OneToMany(mappedBy = "applicationFormVersion", cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 32)
    private final List<Question> questions = new ArrayList<>();

    private ApplicationFormVersion(
            final EventApplicationForm applicationForm,
            final int versionNumber,
            final List<Question> questions
    ) {
        this.applicationForm = applicationForm;
        this.versionNumber = versionNumber;
        questions.forEach(question -> question.assignTo(this));
        this.questions.addAll(questions);
    }

    public static ApplicationFormVersion create(
            final EventApplicationForm applicationForm,
            final int versionNumber,
            final List<Question> questions
    ) {
        return new ApplicationFormVersion(applicationForm, versionNumber, questions);
    }

    public boolean hasVersionNumber(final int versionNumber) {
        return this.versionNumber == versionNumber;
    }

    public boolean belongsTo(final Event event) {
        return applicationForm.isFor(event);
    }

    public boolean hasQuestion(final Question question) {
        return question.belongsTo(this);
    }

    public Set<Question> getRequiredQuestions() {
        return questions.stream()
                .filter(Question::isRequired)
                .collect(Collectors.toSet());
    }
}
