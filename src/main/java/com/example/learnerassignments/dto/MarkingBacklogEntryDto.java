package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MarkingBacklogEntryDto {
    private Long staffId;
    private String fullName;
    private String role;
    private Long moduleCount;
    private Long unmarkedCount;
    private LocalDateTime oldestUnmarkedAt;
}
