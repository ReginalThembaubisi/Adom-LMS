package com.example.learnerassignments.dto;

import com.example.learnerassignments.model.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmittedLearnerDto {

    private Long submissionId;
    private Long learnerId;
    private String learnerCode;
    private String fullName;
    private String cohort;
    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private String filePath;
    private String originalFilename;
    private String feedback;
    private LocalDateTime gradedAt;
    private String gradedByRole;
    private String gradedByName;
}
