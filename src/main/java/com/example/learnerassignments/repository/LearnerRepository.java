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
}
