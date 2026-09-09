package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Login and registration response: the learner's own profile plus the session token the
 * portal presents on every subsequent request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerAuthResponse {

    private String token;
    private LocalDateTime expiresAt;
    private LearnerResponse learner;
}
