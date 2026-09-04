package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    Optional<Lecturer> findByUsername(String username);
    boolean existsByUsername(String username);

    @Query("""
            SELECT DISTINCT cat.lecturer
            FROM Learner l
            JOIN l.modules mod
            JOIN mod.category cat
            WHERE l.id = :learnerId AND cat.lecturer IS NOT NULL
            """)
    List<Lecturer> findFacilitatorsForLearner(@Param("learnerId") Long learnerId);

    @Query("""
            SELECT COUNT(l) > 0
            FROM Learner l
            JOIN l.modules mod
            JOIN mod.category cat
            WHERE l.id = :learnerId AND cat.lecturer.id = :lecturerId
            """)
    boolean isFacilitatorOfLearner(@Param("lecturerId") Long lecturerId, @Param("learnerId") Long learnerId);
}
