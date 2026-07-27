package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ModuleFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleFileRepository extends JpaRepository<ModuleFile, Long> {
    List<ModuleFile> findByModuleId(Long moduleId);
}
