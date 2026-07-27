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
public class StudentSubmissionHistoryDto {

    private Long submissionId;
    private String sessionName;
    private String assignmentTitle;
    private String moduleName;
    private String originalFilename;
    private LocalDateTime submittedAt;
    private String status;
    private Integer grade;
    private String feedback;
    private LocalDateTime gradedAt;
    private String gradedFilePath;
    private String gradedOriginalFilename;
}
