package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One piece of released, learner-facing marking. Never a draft, never an internal report. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerFeedbackDto {
    private Long submissionId;
    private String moduleName;
    private String assignmentTitle;
    private String sessionName;
    private String originalFilename;
    private String outcome;
    private Integer marksAwarded;
    private String feedback;
    private String gradedByRole;
    private String gradedByName;
    private LocalDateTime publishedAt;
    private boolean hasMarkedCopy;
    private boolean hasAnnotations;
}
