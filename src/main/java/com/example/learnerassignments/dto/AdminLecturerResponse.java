package com.example.learnerassignments.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminLecturerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String username;
    private LocalDateTime createdAt;
}
