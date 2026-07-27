package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findBySessionId(Long sessionId);

    List<Submission> findByLearner_LearnerCodeOrderBySubmittedAtDesc(String learnerCode);

    boolean existsByLearnerIdAndSessionId(Long learnerId, Long sessionId);
}
