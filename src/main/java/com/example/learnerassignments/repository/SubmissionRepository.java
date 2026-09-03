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
}
