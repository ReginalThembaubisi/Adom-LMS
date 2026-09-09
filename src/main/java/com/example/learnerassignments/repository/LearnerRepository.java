package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Learner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LearnerRepository extends JpaRepository<Learner, Long> {

    Optional<Learner> findByLearnerCode(String learnerCode);

    boolean existsByLearnerCode(String learnerCode);

    java.util.List<Learner> findByModules_Id(Long moduleId);

    java.util.List<Learner> findDistinctByModulesCategoryLecturerId(Long lecturerId);

    /** Ids of learners enrolled on any of these modules, read through the join table. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT l.id FROM Learner l JOIN l.modules m WHERE m.id IN :moduleIds")
    java.util.List<Long> findIdsByModuleIdIn(
            @org.springframework.data.repository.query.Param("moduleIds") java.util.Collection<Long> moduleIds);
}
