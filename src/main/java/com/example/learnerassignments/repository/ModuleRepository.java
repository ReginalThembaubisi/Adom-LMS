package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {
    List<Module> findByCategoryLecturerId(Long lecturerId);

    List<Module> findByCategoryLearnershipId(Long learnershipId);

    /** Every module in one category — the PoE export's SECTION scope narrows to these. */
    List<Module> findByCategoryId(Long categoryId);

    /**
     * Modules any of these learners is enrolled on.
     *
     * Queried through the join table rather than by reading Module.learners, which is the
     * inverse side of the mapping: that collection reflects whatever the current persistence
     * context happens to know, so filtering on it silently returns nothing when the owning
     * side was written in the same transaction.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT m FROM Module m JOIN m.learners l WHERE l.id IN :learnerIds")
    java.util.List<com.example.learnerassignments.model.Module> findByLearnerIdIn(
            @org.springframework.data.repository.query.Param("learnerIds") java.util.Collection<Long> learnerIds);
}
