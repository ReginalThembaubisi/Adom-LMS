package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.LegacyImportEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyImportEntryRepository extends JpaRepository<LegacyImportEntry, Long> {

    List<LegacyImportEntry> findByBatch_IdOrderByIdAsc(Long batchId);

    void deleteByBatch_Id(Long batchId);
}
