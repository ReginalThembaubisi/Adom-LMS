package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.SubmissionGradingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionGradingHistoryRepository extends JpaRepository<SubmissionGradingHistory, Long> {

    List<SubmissionGradingHistory> findBySubmission_IdOrderByGradedAtAsc(Long submissionId);
}
