package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateLecturerRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;
    private String email;
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Password is required")
    private String password;
}
