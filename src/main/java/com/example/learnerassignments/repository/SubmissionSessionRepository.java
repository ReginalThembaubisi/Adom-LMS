package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.SessionStatus;
import com.example.learnerassignments.model.SubmissionSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionSessionRepository extends JpaRepository<SubmissionSession, Long> {

    List<SubmissionSession> findAllByOrderByCreatedAtDesc();

    List<SubmissionSession> findByAssignmentId(Long assignmentId);

    List<SubmissionSession> findByAssignmentModuleIdInOrderByCreatedAtDesc(Collection<Long> moduleIds);

    /** Sessions for these modules, assignment and module fetched with them since every caller reads both. */
    @EntityGraph(attributePaths = {"assignment", "assignment.module"})
    List<SubmissionSession> findByAssignmentModuleIdIn(Collection<Long> moduleIds);

    @Query("SELECT s FROM SubmissionSession s WHERE s.status = 'OPEN' OR (s.startTime <= :now AND s.endTime >= :now AND s.status <> 'CLOSED') ORDER BY s.createdAt DESC")
    List<SubmissionSession> findActiveSessions(@Param("now") LocalDateTime now);

    List<SubmissionSession> findByStatusAndStartTimeLessThanEqualAndEndTimeGreaterThan(
            SessionStatus status, LocalDateTime nowStart, LocalDateTime nowEnd);

    @Query("SELECT s FROM SubmissionSession s WHERE s.status <> 'CLOSED' AND s.endTime <= :now")
    List<SubmissionSession> findExpiredNonClosedSessions(@Param("now") LocalDateTime now);

    /**
     * (sessionId, sessionName, endTime, moduleId) for the modules given.
     *
     * A projection rather than entities: the dashboard needs four columns per session and
     * nothing else, and the @SQLRestriction on the entity still keeps deleted sessions out.
     * Sessions whose assignment has no module are excluded by the join — there is no enrolled
     * learner they could be expected of.
     */
    @Query("""
           SELECT s.id, s.sessionName, s.endTime, m.id
           FROM SubmissionSession s
           JOIN s.assignment a
           JOIN a.module m
           WHERE m.id IN :moduleIds
           """)
    List<Object[]> findSessionModulePairs(@Param("moduleIds") Collection<Long> moduleIds);
}
