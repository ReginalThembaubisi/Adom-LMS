package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One thing the learner was told. No storage paths, no other learner, no link. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    private Long id;
    private String type;
    private String refType;
    private Long refId;
    private String body;
    private LocalDateTime createdAt;
    private boolean read;
}
