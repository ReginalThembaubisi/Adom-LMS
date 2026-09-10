package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "module_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "module")
@ToString(exclude = "module")
public class ModuleFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType; // e.g. "Syllabus", "Slides", "Notes"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- Portfolio of Evidence columns (Phase 2) ---
    // Added and backfilled only. Nothing reads them yet; the phases that do are named below.
    //
    // All nullable at the database level, deliberately. These columns are being added to a
    // table that already has rows, and a NOT NULL column cannot be added to a populated table
    // without a database default — which would fail the boot that ships them, on a schema
    // managed by ddl-auto=update where the DDL is not under our control. New rows get their
    // values from the builder defaults and @PrePersist below; existing rows get theirs from
    // PoeSchemaBackfill. A later phase can tighten them once no nulls remain.

    /** Which numbered PoE folder this file belongs in. Facilitator guides are section 3. */
    @Column(name = "poe_section")
    @Builder.Default
    private Integer poeSection = 3;

    /** Bumped when a guide is reissued, so a learner's pinned version stays resolvable (Phase 8). */
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /** False once a newer version supersedes this one. Superseded rows are kept, not deleted. */
    @Column(name = "is_current")
    @Builder.Default
    private Boolean current = true;

    /** Scheduled release; null means visible as soon as it exists. */
    @Column(name = "visible_from")
    private LocalDateTime visibleFrom;

    /** Content hash, computed at upload. Phase 9 signs against it without fetching the file. */
    @Column(name = "sha256", length = 64)
    private String sha256;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.poeSection == null) {
            this.poeSection = 3;
        }
        if (this.version == null) {
            this.version = 1;
        }
        if (this.current == null) {
            this.current = true;
        }
    }
}
