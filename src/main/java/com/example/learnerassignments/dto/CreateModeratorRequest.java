package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModeratorRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;
    private String email;
    @NotBlank(message = "Username is required")
    private String username;
    private String password;
}
