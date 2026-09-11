package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * One file found inside a legacy import zip, and what the preview decided about it.
 *
 * Rows exist for every real file in the zip, importable or not — a file the importer will not
 * write is still something the admin has to be able to see and act on, per the brief's own
 * acceptance criterion that a preview shows every file. Directory entries and known junk
 * (`__MACOSX`, `.DS_Store`) never get a row; there is nothing an admin could do about those.
 */
@Entity
@Table(
        name = "legacy_import_entries",
        indexes = @Index(name = "idx_legacy_import_entries_batch", columnList = "batch_id")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"batch", "matchedLearner"})
@ToString(exclude = {"batch", "matchedLearner"})
public class LegacyImportEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private LegacyImportBatch batch;

    /** The full path inside the zip, exactly as it appeared — kept for the admin to recognise
     *  the file, and because the confirm step re-opens the same zip by this path. */
    @Column(name = "zip_entry_path", nullable = false, length = 1000)
    private String zipEntryPath;

    /** The top-level folder this entry was found under, in "many learners" mode. Null in
     *  "single learner" mode, where the admin named the learner directly. */
    @Column(name = "top_folder", length = 255)
    private String topFolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_learner_id")
    private Learner matchedLearner;

    /** Denormalised so the preview still reads sensibly if the learner is ever renamed or
     *  removed between preview and confirm. */
    @Column(name = "matched_learner_name", length = 255)
    private String matchedLearnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_confidence", nullable = false, length = 20)
    private LegacyImportMatchConfidence matchConfidence;

    /** Null when this row is not going to be imported at all (no learner match, or an
     *  out-of-scope / unsupported file). */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30)
    private PoeDocumentType documentType;

    @Column(name = "importable", nullable = false)
    private boolean importable;

    /** Why this row will not be imported, or a note about how it was classified — shown to the
     *  admin as written, never silently dropped. */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Filled in once {@code confirm} actually writes this row, so a re-opened preview after
     *  confirmation can show what it became rather than reporting "not imported". */
    @Column(name = "imported_document_id")
    private Long importedDocumentId;

    @Column(name = "import_error", length = 500)
    private String importError;
}
