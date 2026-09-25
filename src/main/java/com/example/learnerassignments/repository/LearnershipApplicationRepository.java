package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ApplicationStatus;
import com.example.learnerassignments.model.LearnershipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearnershipApplicationRepository extends JpaRepository<LearnershipApplication, Long> {

    Optional<LearnershipApplication> findByReference(String reference);

    boolean existsByReference(String reference);

    boolean existsByLearnership_IdAndIdNumberAndStatusIn(Long learnershipId, String idNumber,
                                                          Collection<ApplicationStatus> statuses);

    long countByLearnership_Id(Long learnershipId);

    // Staff lists, newest first, with the learnership fetched so a list is one query. Two
    // queries rather than one with an optional ":id IS NULL OR" parameter, which PostgreSQL
    // rejects when the parameter is bound as null ("could not determine data type").
    @Query("SELECT a FROM LearnershipApplication a JOIN FETCH a.learnership LEFT JOIN FETCH a.enrolledLearner ORDER BY a.submittedAt DESC")
    List<LearnershipApplication> findAllWithLearnership();

    @Query("SELECT a FROM LearnershipApplication a JOIN FETCH a.learnership l LEFT JOIN FETCH a.enrolledLearner " +
            "WHERE l.id = :learnershipId ORDER BY a.submittedAt DESC")
    List<LearnershipApplication> findByLearnershipWithLearnership(@Param("learnershipId") Long learnershipId);

    /** Application counts per learnership and status, for the adverts list and pipeline tabs. */
    @Query("SELECT a.learnership.id, a.status, COUNT(a) FROM LearnershipApplication a " +
            "GROUP BY a.learnership.id, a.status")
    List<Object[]> countByLearnershipAndStatus();
}
