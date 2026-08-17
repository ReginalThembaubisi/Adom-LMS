package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Assessor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessorRepository extends JpaRepository<Assessor, Long> {
    Optional<Assessor> findByUsername(String username);
    boolean existsByUsername(String username);
}
