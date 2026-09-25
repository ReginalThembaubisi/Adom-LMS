package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Learner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LearnerRepository extends JpaRepository<Learner, Long> {

    Optional<Learner> findByLearnerCode(String learnerCode);

    /**
     * The same, holding a row lock until the transaction ends. Submitting takes this first so
     * two uploads from one learner (a double click on a slow connection) run one after the
     * other: the second sees the first's row and replaces it instead of adding another.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT l FROM Learner l WHERE l.learnerCode = :learnerCode")
    Optional<Learner> findByLearnerCodeForUpdate(
            @org.springframework.data.repository.query.Param("learnerCode") String learnerCode);

    boolean existsByLearnerCode(String learnerCode);

    /** Every learner id, without hydrating the entities. */
    @org.springframework.data.jpa.repository.Query("SELECT l.id FROM Learner l")
    java.util.List<Long> findAllIds();

    java.util.List<Learner> findByModules_Id(Long moduleId);

    java.util.List<Learner> findDistinctByModulesCategoryLecturerId(Long lecturerId);

    /** Ids of learners enrolled on any of these modules, read through the join table. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT l.id FROM Learner l JOIN l.modules m WHERE m.id IN :moduleIds")
    java.util.List<Long> findIdsByModuleIdIn(
            @org.springframework.data.repository.query.Param("moduleIds") java.util.Collection<Long> moduleIds);

    /** Everyone on a learnership, for enrolling them onto a module created after they joined. */
    java.util.List<Learner> findByLearnership_Id(Long learnershipId);

    /**
     * (learnerId, moduleId) for a set of learners, read straight off the join table.
     *
     * The alternative is touching learner.getModules() per learner, which is one query each and
     * turns a 300-learner dashboard into 300 round trips.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT l.id, m.id FROM Learner l JOIN l.modules m WHERE l.id IN :learnerIds")
    java.util.List<Object[]> findLearnerModulePairs(
            @org.springframework.data.repository.query.Param("learnerIds") java.util.Collection<Long> learnerIds);

    /** The cohort names actually in use, for the dashboard filter. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT l.cohort FROM Learner l WHERE l.cohort IS NOT NULL AND l.cohort <> '' ORDER BY l.cohort")
    java.util.List<String> findDistinctCohorts();

    /** Everyone in one cohort within one learnership — the PoE export's COHORT scope. */
    java.util.List<Learner> findByLearnership_IdAndCohort(Long learnershipId, String cohort);
}
