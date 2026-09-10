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
    /** The brief's real filename, for the download; never its storage location. */
    private String taskFileName;
    /** Whether a brief is attached. The Home card's Brief button used to test taskFilePath,
     *  which this DTO has never carried, so the button could never appear. */
    private boolean hasBrief;
}
