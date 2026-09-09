package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A learner's authenticated session. The raw bearer token is never persisted — only its
 * SHA-256 digest — so a leaked database dump cannot be replayed as a live session.
 * Sessions are revoked by stamping {@code revokedAt} rather than deleting the row.
 */
@Entity
@Table(
        name = "learner_sessions",
        indexes = {
            @Index(name = "idx_learner_sessions_token_hash", columnList = "token_hash"),
            @Index(name = "idx_learner_sessions_learner", columnList = "learner_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"learner"})
@ToString(exclude = {"learner"})
public class LearnerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isUsableAt(LocalDateTime moment) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(moment);
    }
}
