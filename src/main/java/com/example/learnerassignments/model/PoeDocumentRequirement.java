package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What a particular learnership requires, and from when.
 *
 * The document *types* stay an enum — that is the vocabulary, and every learnership so far
 * wants the same words. What differs between qualifications is which of them are actually
 * required, so that belongs here rather than in the codebase: a learnership that does not ask
 * for a matric certificate should not show its whole cohort red for a document nobody wanted.
 *
 * {@code requiredFrom} is what stops a cohort that started before this system existed appearing
 * entirely incomplete. A learner who registered before the date a requirement took effect was
 * never asked for that document, so counting it as missing would be an accusation rather than a
 * measurement — and a dashboard full of red nobody can act on is a dashboard nobody reads.
 */
@Entity
@Table(
    name = "poe_document_requirements",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_requirement_learnership_type",
        columnNames = {"learnership_id", "document_type"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoeDocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learnership_id", nullable = false)
    private Learnership learnership;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private PoeDocumentType documentType;

    /** False keeps the row as a record that this learnership deliberately does not want it. */
    @Column(nullable = false)
    @Builder.Default
    private boolean required = true;

    /**
     * The date this became a requirement. Learners who registered before it are not counted as
     * missing the document. Null means it has always applied.
     */
    @Column(name = "required_from")
    private LocalDate requiredFrom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Whether this requirement applies to a learner who registered at the given time.
     *
     * Null-defensive on both sides: a learner with no registration date is treated as covered,
     * because the alternative is silently exempting somebody from everything.
     */
    public boolean appliesTo(LocalDateTime learnerRegisteredAt) {
        if (!required) {
            return false;
        }
        if (requiredFrom == null || learnerRegisteredAt == null) {
            return true;
        }
        return !learnerRegisteredAt.toLocalDate().isBefore(requiredFrom);
    }

    /**
     * Whether this learner sits before the date the requirement took effect.
     *
     * Distinct from {@link #appliesTo}, and deliberately so. A requirement the learnership
     * switched off is not on the checklist at all; a requirement this learner simply predates
     * still is, shown as exempt rather than missing. Collapsing the two would either accuse a
     * learner of withholding a document nobody asked them for, or quietly report them complete
     * when a SETA auditor will not.
     */
    public boolean predatesRequirement(LocalDateTime learnerRegisteredAt) {
        if (!required || requiredFrom == null || learnerRegisteredAt == null) {
            return false;
        }
        return learnerRegisteredAt.toLocalDate().isBefore(requiredFrom);
    }
}
