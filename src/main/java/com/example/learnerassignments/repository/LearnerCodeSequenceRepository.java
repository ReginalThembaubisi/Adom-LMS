package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.LearnerCodeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LearnerCodeSequenceRepository extends JpaRepository<LearnerCodeSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearnerCodeSequence> findByYearVal(Integer yearVal);
}
