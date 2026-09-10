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
    /**
     * Where the file is stored.
     *
     * Left unset on learner-facing responses: a client needs to know a file exists, not where
     * it lives, and for a legacy row this is a public URL to course material. Learners
     * download through /api/me/module-files/{id}/download, which checks enrolment first and
     * sends the real filename back. Still populated by the staff controllers, whose dashboards
     * link to it directly.
     */
    private String filePath;
    private String originalFilename;
    private String fileType;
}
