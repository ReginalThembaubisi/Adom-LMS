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
    // No filePath. Echoing the storage location back to the uploader served no caller and,
    // for a legacy row, is a public URL to the file.
    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private String message;
}
