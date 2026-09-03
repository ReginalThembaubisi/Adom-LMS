package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {
    List<Module> findByCategoryLecturerId(Long lecturerId);

    List<Module> findByCategoryLearnershipId(Long learnershipId);
}
