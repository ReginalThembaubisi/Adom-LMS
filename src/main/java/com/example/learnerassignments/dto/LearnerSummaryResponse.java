package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerSummaryResponse {

    private Long id;
    private String learnerCode;
    private String fullName;
    private String cohort;
}
