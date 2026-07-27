package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleDetailResponseDto {
    private Long id;
    private String moduleName;
    private String moduleCode;
    private String lecturerName;
    private String fileName;
    private String filePath;
    private String moduleType;
    private java.util.List<ModuleSlotDto> slots;
    private java.util.List<ModuleFileDto> files;
}
