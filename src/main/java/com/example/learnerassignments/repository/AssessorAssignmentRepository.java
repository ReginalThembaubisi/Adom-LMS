package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.AssessorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssessorAssignmentRepository extends JpaRepository<AssessorAssignment, Long> {

    List<AssessorAssignment> findByAssessor_Id(Long assessorId);

    long countByAssessor_Id(Long assessorId);

    void deleteByAssessor_IdAndId(Long assessorId, Long id);

    /**
     * Learner ids this assessor may reach: named directly, or enrolled on a module in a
     * category they hold. Returns nothing when they hold no rows — the query cannot be
     * written in a way that treats an empty assignment set as a wildcard.
     */
    @Query("""
           SELECT DISTINCT l.id FROM AssessorAssignment a
             JOIN Learner l ON (
                  l.id = a.learner.id
               OR EXISTS (SELECT m FROM Module m JOIN m.learners ml
                          WHERE ml.id = l.id AND m.category.id = a.category.id)
             )
           WHERE a.assessor.id = :assessorId
           """)
    List<Long> findAccessibleLearnerIds(@Param("assessorId") Long assessorId);
}
