package com.example.learnerassignments.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminModuleResponse {
    private Long id;
    private String moduleName;
    private String moduleCode;
    private Long lecturerId;
    private String lecturerName;
    private String filePath;
    private String moduleType;
    private java.util.List<ModuleFileDto> files;
}
