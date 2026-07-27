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
public class LearnerResponse {

    private Long id;
    private String learnerCode;
    private String fullName;
    private String email;
    private String idNumber;
    private String phoneNumber;
    private String cohort;
    private Long learnershipId;
    private String learnershipName;
    private LocalDateTime createdAt;
}
