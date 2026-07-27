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
public class TimelineResponseDto {
    private Long sessionId;
    private Long moduleId;
    private String moduleName;
    private String slotTitle;
    private String description;
    private LocalDateTime endTime;
    private String status;
    private boolean submitted;
}
