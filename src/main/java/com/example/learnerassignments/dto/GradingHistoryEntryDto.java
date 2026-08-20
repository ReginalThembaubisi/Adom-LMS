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
public class GradingHistoryEntryDto {

    private SubmissionStatus outcome;
    private String feedback;
    private Integer marksAwarded;
    private String gradedByRole;
    private String gradedByName;
    private LocalDateTime gradedAt;
}
