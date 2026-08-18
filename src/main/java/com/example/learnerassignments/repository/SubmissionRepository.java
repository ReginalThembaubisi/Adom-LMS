package com.example.learnerassignments.repository;

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
}
