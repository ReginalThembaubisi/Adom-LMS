package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLearnerRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    private String idNumber; // Optional SA ID number

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Cohort is required")
    private String cohort;

    private Long learnershipId;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
