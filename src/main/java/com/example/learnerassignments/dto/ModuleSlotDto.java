package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleSlotDto {
    private Long id; // SubmissionSession ID (the slot)
    private String title; // Assignment Title
    private String description; // Assignment Description
    private LocalDateTime startTime; // When the submission session opens
    private LocalDateTime endTime; // Deadline of the submission session
    private String sessionName; // Submission Session Name
    private String status; // Session Status (SCHEDULED, OPEN, CLOSED)
    private boolean isSubmitted; // True if logged-in learner has submitted to this session
    private String taskFileName;
    private String taskFilePath;
}
