package com.example.learnerassignments.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.time.Year;

@Entity
@Table(name = "learners")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"modules", "learnership"})
@ToString(exclude = {"modules", "learnership"})
public class Learner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "learner_code", nullable = false, unique = true, length = 64)
    private String learnerCode;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "id_number", length = 30)
    private String idNumber;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "cohort", length = 100)
    private String cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learnership_id")
    private Learnership learnership;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "reset_code", length = 6)
    private String resetCode;

    @Column(name = "reset_code_expires_at")
    private LocalDateTime resetCodeExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "learner_modules",
        joinColumns = @JoinColumn(name = "learner_id"),
        inverseJoinColumns = @JoinColumn(name = "module_id"),
        indexes = {
            @Index(name = "idx_learner_modules_learner", columnList = "learner_id"),
            @Index(name = "idx_learner_modules_module", columnList = "module_id")
        }
    )
    private java.util.Set<Module> modules;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.learnerCode == null || this.learnerCode.isBlank()) {
            int currentYear = Year.now().getValue();
            int randomSeq = (int) (Math.random() * 90000) + 10000;
            this.learnerCode = String.format("%d%05d", currentYear, randomSeq);
        }
    }
}
