package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.LegacyImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyImportBatchRepository extends JpaRepository<LegacyImportBatch, Long> {

    List<LegacyImportBatch> findAllByOrderByCreatedAtDesc();
}
