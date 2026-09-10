package com.example.learnerassignments.repository;

import com.example.learnerassignments.dto.MarkingBacklogEntryDto;
import com.example.learnerassignments.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findBySessionId(Long sessionId);

    List<Submission> findByLearner_LearnerCodeOrderBySubmittedAtDesc(String learnerCode);

    boolean existsByLearnerIdAndSessionId(Long learnerId, Long sessionId);

    // Soft-delete: marks submissions as deleted instead of removing the rows,
    // so they remain in the database and can be restored if a session was deleted by mistake.
    @Modifying
    @Query("UPDATE Submission s SET s.deletedAt = :now WHERE s.session.id = :sessionId AND s.deletedAt IS NULL")
    void softDeleteBySessionId(@Param("sessionId") Long sessionId, @Param("now") LocalDateTime now);

    // Aggregates per-facilitator unmarked (SUBMITTED) submission counts via the full FK chain:
    // Submission → SubmissionSession → Assignment → Module → Category → Lecturer.
    // Only lecturers with at least one unmarked submission appear in the result.
    @Query("""
            SELECT new com.example.learnerassignments.dto.MarkingBacklogEntryDto(
                s.session.assignment.module.category.lecturer.id,
                s.session.assignment.module.category.lecturer.fullName,
                'FACILITATOR',
                COUNT(DISTINCT s.session.assignment.module.id),
                COUNT(s.id),
                MIN(s.submittedAt)
            )
            FROM Submission s
            WHERE s.status = com.example.learnerassignments.model.SubmissionStatus.SUBMITTED
              AND s.session.assignment.module.category.lecturer IS NOT NULL
            GROUP BY s.session.assignment.module.category.lecturer.id,
                     s.session.assignment.module.category.lecturer.fullName
            ORDER BY COUNT(s.id) DESC
            """)
    List<MarkingBacklogEntryDto> findFacilitatorMarkingBacklog();

    /**
     * Submissions whose learner has no learner_modules row for the module the session belongs
     * to — the ones the grading console could not show before the list stopped being derived
     * from the roster (finding 4.2d). Counted rather than inferred, because the alternative is
     * guessing whether any learner work went unmarked.
     */
    @Query("""
           SELECT COUNT(s) FROM Submission s
           WHERE NOT EXISTS (
               SELECT 1 FROM Learner l JOIN l.modules m
               WHERE l.id = s.learner.id
                 AND m.id = s.session.assignment.module.id
           )
           """)
    long countMissingFromRoster();

    /** The same rows, newest first, for following up with the learners concerned. */
    @Query("""
           SELECT s FROM Submission s
           WHERE NOT EXISTS (
               SELECT 1 FROM Learner l JOIN l.modules m
               WHERE l.id = s.learner.id
                 AND m.id = s.session.assignment.module.id
           )
           ORDER BY s.submittedAt DESC
           """)
    List<Submission> findMissingFromRoster(org.springframework.data.domain.Pageable pageable);

    /**
     * Of those, the ones nobody has graded — the actual harm, counted across the whole table.
     *
     * The first version of this derived the number from the twenty-row sample, so it could
     * never report more than twenty however much work was affected. A count that silently
     * caps itself is worse than no count: it reads as reassurance.
     */
    @Query("""
           SELECT COUNT(s) FROM Submission s
           WHERE s.gradedAt IS NULL
             AND NOT EXISTS (
               SELECT 1 FROM Learner l JOIN l.modules m
               WHERE l.id = s.learner.id
                 AND m.id = s.session.assignment.module.id
           )
           """)
    long countMissingFromRosterUngraded();

    /** How stored file paths are shaped, which says when each storage era actually applied. */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.filePath LIKE 'http%'")
    long countLegacyUrlPaths();

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.filePath LIKE 'lms_secure/%'")
    long countAuthenticatedPublicIds();

    /**
     * Every (learner, module) pair a submission proves. Someone who submitted work for a module
     * was enrolled on it, whatever the join table says.
     */
    @Query("""
           SELECT DISTINCT s.learner.id, s.session.assignment.module.id
           FROM Submission s
           WHERE s.learner IS NOT NULL
             AND s.session.assignment.module IS NOT NULL
           """)
    List<Object[]> findEnrolmentPairsProvenBySubmissions();

    /**
     * (learnerId, sessionId, gradedAt) for a set of learners.
     *
     * A projection, not entities: the completeness dashboard needs to know whether a learner
     * submitted to a session and whether it has been marked, and nothing else. The entity's
     * @SQLRestriction still keeps deleted submissions out.
     */
    @Query("""
           SELECT s.learner.id, s.session.id, s.gradedAt
           FROM Submission s
           WHERE s.learner.id IN :learnerIds
           """)
    List<Object[]> findLearnerSessionMarkingPairs(
            @org.springframework.data.repository.query.Param("learnerIds") java.util.Collection<Long> learnerIds);
}
