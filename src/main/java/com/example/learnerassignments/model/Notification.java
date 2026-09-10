package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Something that happened which a person should be told about.
 *
 * The table is Phase 6's, built to its shape here because Phase 5 has to write the
 * feedback-released row and there is no sense inventing a temporary one to throw away. Phase 5
 * only writes; Phase 6 adds the badge counts and the live push that read.
 *
 * The recipient is a user id plus a role rather than a foreign key, because learners, lecturers,
 * assessors, moderators and admins live in five separate tables and a notification has to be
 * able to address any of them.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "user_id,user_role"),
        @Index(name = "idx_notifications_unread", columnList = "user_id,user_role,read_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 20)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /** What the notification is about, e.g. "SESSION", so a client can route to it. */
    @Column(name = "ref_type", length = 40)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /**
     * What the person is told, in full.
     *
     * Never a link. Learners are told in the announcement that this system will not send them
     * one, so a notification containing a URL is indistinguishable from a phishing message and
     * teaches them to click things.
     */
    @Column(length = 500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
