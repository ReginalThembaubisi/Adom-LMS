package com.example.learnerassignments.dto;

import com.example.learnerassignments.model.SubmissionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GradeSubmissionRequest {
    @NotNull(message = "Outcome is required")
    private SubmissionStatus outcome;

    private String feedback;
}
