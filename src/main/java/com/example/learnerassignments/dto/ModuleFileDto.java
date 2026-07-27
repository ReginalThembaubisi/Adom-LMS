package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleFileDto {
    private Long id;
    private String title;
    private String filePath;
    private String originalFilename;
    private String fileType;
}
