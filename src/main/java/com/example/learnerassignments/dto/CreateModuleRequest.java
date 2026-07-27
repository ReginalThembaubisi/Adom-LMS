package com.example.learnerassignments.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModuleRequest {
    @NotBlank(message = "Module name is required")
    private String moduleName;
    private String moduleCode;
    private Long categoryId;
    private String filePath;
}
