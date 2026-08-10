package com.ahmadda.domain.event;


import com.ahmadda.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE question SET deleted_at = CURRENT_TIMESTAMP WHERE question_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Question extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false, updatable = false)
    private ApplicationFormVersion applicationFormVersion;

    @Column(nullable = false)
    private String questionText;

    @Column(nullable = false)
    private boolean isRequired;

    @Column(nullable = false)
    private int orderIndex;

    private Question(
            final String questionText,
            final boolean isRequired,
            final int orderIndex
    ) {
        this.questionText = questionText;
        this.isRequired = isRequired;
        this.orderIndex = orderIndex;
    }

    public static Question create(
            final String questionText,
            final boolean isRequired,
            final int orderIndex
    ) {
        return new Question(questionText, isRequired, orderIndex);
    }

    void assignTo(final ApplicationFormVersion applicationFormVersion) {
        if (this.applicationFormVersion != null) {
            throw new IllegalStateException("질문은 하나의 신청서 버전에만 속할 수 있습니다.");
        }

        this.applicationFormVersion = applicationFormVersion;
    }

    boolean belongsTo(final ApplicationFormVersion applicationFormVersion) {
        if (this.applicationFormVersion == null) {
            return false;
        }
        if (this.applicationFormVersion == applicationFormVersion) {
            return true;
        }
        if (this.applicationFormVersion.getId() == null || applicationFormVersion.getId() == null) {
            return false;
        }

        return this.applicationFormVersion.getId()
                .equals(applicationFormVersion.getId());
    }
}
