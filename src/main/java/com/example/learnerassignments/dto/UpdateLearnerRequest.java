package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLearnerRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    private String idNumber;

    private String phoneNumber;

    private String cohort;

    private Long learnershipId;
}
