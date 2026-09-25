package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.model.LearnershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnershipRepository extends JpaRepository<Learnership, Long> {

    Optional<Learnership> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Learnership> findByStatusOrderByClosingDateAscNameAsc(LearnershipStatus status);
}
