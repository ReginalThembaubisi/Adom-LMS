package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One row per conversation partner — a facilitator (from the student's side) or a
// student (from the facilitator's side).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageThreadSummaryDto {
    private Long partnerId;
    private String partnerName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
