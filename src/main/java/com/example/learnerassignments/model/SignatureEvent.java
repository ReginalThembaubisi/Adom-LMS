package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One act of signing one document. Append-only, following the pattern
 * {@link SubmissionGradingHistory} already established.
 *
 * <p>Every field below is written once, at {@link #signedAt}, and never again, with two
 * documented exceptions:
 * <ul>
 *   <li>{@code revokedAt} / {@code revokedReason} — withdrawal, set once when a resubmission or
 *       a new document version supersedes what this event attests to. This is the exception the
 *       brief itself names: "withdrawal sets revokedAt", not an update to what was signed.</li>
 *   <li>{@code stampedFilePath} — filled in once, asynchronously, after the row already exists.
 *       Generating the signature-certificate PDF (PDFBox render plus QR) is I/O and CPU work
 *       this phase deliberately keeps off the signing request's thread; recording where the
 *       result landed is not a change to any fact this row attests to, so it does not weaken
 *       the append-only guarantee on the fields that matter for verification.</li>
 * </ul>
 *
 * <p>{@code id} is a plain {@code Long}, not the {@code UUID} the brief's sketch shows — every
 * other entity in this codebase uses an identity-column {@code Long}, and {@code
 * verificationCode} already carries the property a UUID primary key would have been for
 * (unguessable, safe to put in a public URL) without introducing the one primary-key type in
 * the schema.
 */
@Entity
@Table(
        name = "signature_events",
        indexes = {
            @Index(name = "idx_signature_events_signable", columnList = "signable_type, signable_id"),
            @Index(name = "idx_signature_events_code", columnList = "verification_code", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "signable_type", nullable = false, length = 30)
    private SignableType signableType;

    @Column(name = "signable_id", nullable = false)
    private Long signableId;

    /** LEARNER today; the column is sized for LECTURER/ASSESSOR/MODERATOR per the brief. */
    @Column(name = "signer_role", nullable = false, length = 20)
    private String signerRole;

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    /**
     * The actual wording shown and agreed to, captured verbatim — not a reference to a template
     * that can be edited later. A future wording change must never rewrite what an old signature
     * legally attests to.
     */
    @Column(name = "declaration_text", nullable = false, columnDefinition = "TEXT")
    private String declarationText;

    /**
     * The drawn specimen, as a base64 PNG data URI, inlined rather than stored as a file
     * ({@code specimenPath} in the brief's sketch).
     *
     * <p>A canvas signature is a few KB; storing it as a normal column avoids adding a second
     * fetch-and-re-serve path only for this one small image, and it never needs to be served
     * unauthenticated — the public verify endpoint never returns it, by the brief's own rule
     * that the page shows no file content.
     */
    @Column(name = "specimen_image", columnDefinition = "TEXT")
    private String specimenImage;

    /** Copied from the target row's own {@code sha256} at signing time — no file fetch needed. */
    @Column(name = "document_sha256", nullable = false, length = 64)
    private String documentSha256;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "verification_code", nullable = false, length = 64)
    private String verificationCode;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    /** Where the generated signature-certificate PDF landed. Null until the async worker runs. */
    @Column(name = "stamped_file_path", length = 500)
    private String stampedFilePath;

    @PrePersist
    protected void onCreate() {
        if (this.signedAt == null) {
            this.signedAt = LocalDateTime.now();
        }
    }
}
