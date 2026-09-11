package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {

    /** An admin's view: every job, newest first. */
    List<ExportJob> findAllByOrderByCreatedAtDesc();

    /** An assessor's or moderator's view: only jobs they requested. */
    List<ExportJob> findByRequestedByRoleAndRequestedByIdOrderByCreatedAtDesc(
            String requestedByRole, Long requestedById);
}
