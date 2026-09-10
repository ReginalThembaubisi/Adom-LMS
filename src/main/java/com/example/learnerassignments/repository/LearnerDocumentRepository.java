package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.LearnerDocument;
import com.example.learnerassignments.model.PoeDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnerDocumentRepository extends JpaRepository<LearnerDocument, Long> {

    /**
     * Every version this learner has ever supplied, newest first.
     *
     * Deliberately unfiltered by current: the portal shows the history of a document that was
     * rejected and resupplied, and hiding superseded rows would hide exactly that.
     */
    List<LearnerDocument> findByLearner_IdOrderByUploadedAtDesc(Long learnerId);

    /**
     * The version of this document type that counts, for this learner.
     *
     * Written as "not superseded" rather than "current = true". The column is NOT NULL on this
     * table so a null cannot arise today, but a filter that drops null rows fails by making a
     * document disappear rather than by raising anything — and a learner reading a screen that
     * says their CV is missing, when the row is right there, has no way to tell those apart.
     */
    @Query("""
           SELECT d FROM LearnerDocument d
           WHERE d.learner.id = :learnerId
             AND d.documentType = :documentType
             AND (d.current IS NULL OR d.current = true)
           ORDER BY d.version DESC
           """)
    List<LearnerDocument> findCurrentForType(@Param("learnerId") Long learnerId,
                                             @Param("documentType") PoeDocumentType documentType);

    /** The highest version number this learner has used for this document type. */
    @Query("""
           SELECT MAX(d.version) FROM LearnerDocument d
           WHERE d.learner.id = :learnerId AND d.documentType = :documentType
           """)
    Optional<Integer> findHighestVersion(@Param("learnerId") Long learnerId,
                                         @Param("documentType") PoeDocumentType documentType);

    /** One document, but only if it belongs to this learner. Used to answer 404 rather than 403. */
    Optional<LearnerDocument> findByIdAndLearner_Id(Long id, Long learnerId);
}
