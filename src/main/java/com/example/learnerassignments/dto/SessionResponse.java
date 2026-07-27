package com.example.learnerassignments.dto;

import com.example.learnerassignments.model.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {

    private Long id;
    private String sessionName;
    private Long assignmentId;
    private String assignmentTitle;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SessionStatus status;
    private LocalDateTime createdAt;
}
