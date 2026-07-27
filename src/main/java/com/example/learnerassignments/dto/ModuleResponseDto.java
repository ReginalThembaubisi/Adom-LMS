package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleResponseDto {
    private Long id;
    private String moduleName;
    private String moduleCode;
    private String lecturerName;
    private String fileName;
    private String filePath;
    private String moduleType;
    private java.util.List<ModuleFileDto> files;
    private java.util.List<ModuleSlotDto> slots;
}
