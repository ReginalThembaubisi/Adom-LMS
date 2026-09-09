package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.LearnerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface LearnerSessionRepository extends JpaRepository<LearnerSession, Long> {

    Optional<LearnerSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE LearnerSession s SET s.revokedAt = :now WHERE s.learner.id = :learnerId AND s.revokedAt IS NULL")
    int revokeAllForLearner(@Param("learnerId") Long learnerId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM LearnerSession s WHERE s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
