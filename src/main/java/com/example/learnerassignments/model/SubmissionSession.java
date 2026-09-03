package com.example.learnerassignments.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "submission_sessions", indexes = {
    @Index(name = "idx_session_status_end_time", columnList = "status, end_time")
})
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Session name is required")
    @Column(name = "session_name", nullable = false)
    private String sessionName;

    @NotNull(message = "Assignment is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "task_file_path", length = 500)
    private String taskFilePath;

    @Column(name = "task_file_name", length = 255)
    private String taskFileName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
