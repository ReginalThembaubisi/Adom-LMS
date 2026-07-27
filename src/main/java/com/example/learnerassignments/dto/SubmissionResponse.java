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
public class SubmissionResponse {

    private Long id;
    private String learnerCode;
    private String learnerName;
    private Long sessionId;
    private String sessionName;
    private Long assignmentId;
    private String assignmentTitle;
    private String originalFilename;
    private String filePath;
    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private String message;
}
