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
        name = "uk_application_form_revision__form_revision_number",
        columnNames = {"application_form_id", "revision_number"}
))
public class ApplicationFormRevision extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_revision_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_form_id", nullable = false, updatable = false)
    private EventApplicationForm applicationForm;

    @Column(nullable = false)
    private int revisionNumber;

    @OneToMany(mappedBy = "formRevision", cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 32)
    private final List<Question> questions = new ArrayList<>();

    private ApplicationFormRevision(
            final EventApplicationForm applicationForm,
            final int revisionNumber,
            final List<Question> questions
    ) {
        this.applicationForm = applicationForm;
        this.revisionNumber = revisionNumber;
        questions.forEach(question -> question.assignTo(this));
        this.questions.addAll(questions);
    }

    public static ApplicationFormRevision create(
            final EventApplicationForm applicationForm,
            final int revisionNumber,
            final List<Question> questions
    ) {
        return new ApplicationFormRevision(applicationForm, revisionNumber, questions);
    }

    public boolean hasRevisionNumber(final int revisionNumber) {
        return this.revisionNumber == revisionNumber;
    }

    public boolean hasQuestion(final Question question) {
        return questions.contains(question);
    }

    public Set<Question> getRequiredQuestions() {
        return questions.stream()
                .filter(Question::isRequired)
                .collect(Collectors.toSet());
    }
}
