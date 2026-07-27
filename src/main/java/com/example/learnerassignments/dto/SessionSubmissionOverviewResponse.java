package com.example.learnerassignments.dto;

import com.example.learnerassignments.model.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSubmissionOverviewResponse {

    private Long sessionId;
    private String sessionName;
    private SessionStatus sessionStatus;
    private Long assignmentId;
    private String assignmentTitle;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime dueDate;
    private int totalLearners;
    private int submittedCount;
    private int unsubmittedCount;
    private List<SubmittedLearnerDto> submitted;
    private List<UnsubmittedLearnerDto> unsubmitted;
}
