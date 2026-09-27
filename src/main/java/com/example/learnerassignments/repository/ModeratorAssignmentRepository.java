package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ModeratorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModeratorAssignmentRepository extends JpaRepository<ModeratorAssignment, Long> {

    List<ModeratorAssignment> findByModerator_Id(Long moderatorId);

    long countByModerator_Id(Long moderatorId);

    /**
     * Learner ids this moderator may reach: on an assigned learnership, and within the
     * assigned cohort when the row names one. Empty when they hold no rows.
     */
    @Query("""
           SELECT DISTINCT l.id FROM ModeratorAssignment a, Learner l
           WHERE a.moderator.id = :moderatorId
             AND l.learnership.id = a.learnership.id
             AND (a.cohort IS NULL OR l.cohort = a.cohort)
           """)
    List<Long> findAccessibleLearnerIds(@Param("moderatorId") Long moderatorId);

    boolean existsByLearnership_Id(Long learnershipId);
}
