package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Learnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearnershipRepository extends JpaRepository<Learnership, Long> {
}
