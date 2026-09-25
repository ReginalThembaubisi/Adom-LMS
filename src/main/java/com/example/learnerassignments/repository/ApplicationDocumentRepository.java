package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    Optional<ApplicationDocument> findByIdAndApplication_Id(Long id, Long applicationId);

    /** [applicationId, count] for every application with at least one document. */
    @Query("SELECT d.application.id, COUNT(d) FROM ApplicationDocument d GROUP BY d.application.id")
    List<Object[]> countPerApplication();
}
