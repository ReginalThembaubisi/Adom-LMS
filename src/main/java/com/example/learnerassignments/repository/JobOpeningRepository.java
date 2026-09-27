package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.JobOpening;
import com.example.learnerassignments.model.LearnershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {

    List<JobOpening> findByStatus(LearnershipStatus status);

    List<JobOpening> findAllByOrderByCreatedAtDesc();
}
