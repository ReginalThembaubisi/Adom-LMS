package com.example.learnerassignments.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOverviewResponse {
    private long lecturersCount;
    private long modulesCount;
    private long submissionsCount;
}
