package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A file an applicant uploaded with their application: their ID copy, school results or CV.
 *
 * Stored exactly like a learner's own documents (private Cloudinary public_id or a path outside
 * the public uploads directory), and typed with the same {@link PoeDocumentType}, so that on
 * enrolment the same stored file becomes the first version in the learner's document vault.
 */
@Entity
@Table(name = "application_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "application")
@ToString(exclude = "application")
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LearnershipApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private PoeDocumentType documentType;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
