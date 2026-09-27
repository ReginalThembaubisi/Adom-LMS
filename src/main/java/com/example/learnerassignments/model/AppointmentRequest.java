package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A business asking for an appointment through the website's Services page. Staff confirm the
 * slot by phone or email and track it here.
 */
@Entity
@Table(name = "appointment_requests", indexes = {
        @Index(name = "idx_appointment_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "company", length = 150)
    private String company;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    /** Software, Cloud, Support, Equipment, Networking, Electrical... */
    @Column(name = "service", nullable = false, length = 60)
    private String service;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time", length = 30)
    private String preferredTime;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.NEW;

    @Column(name = "staff_notes", columnDefinition = "TEXT")
    private String staffNotes;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
