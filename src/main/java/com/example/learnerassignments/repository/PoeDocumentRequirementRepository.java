package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.PoeDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PoeDocumentRequirementRepository extends JpaRepository<PoeDocumentRequirement, Long> {

    List<PoeDocumentRequirement> findByLearnership_Id(Long learnershipId);

    boolean existsByLearnership_Id(Long learnershipId);

    /** Every learnership's requirements in one read, for the cohort-wide dashboard. */
    List<PoeDocumentRequirement> findByLearnership_IdIn(Collection<Long> learnershipIds);
}
