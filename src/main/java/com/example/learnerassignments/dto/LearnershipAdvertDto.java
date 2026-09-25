package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * A learnership advert as the public website shows it. Nothing here is internal: no counts,
 * no staff notes, no learner data — only what the admin wrote for applicants to read.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnershipAdvertDto {
    private Long id;
    private String slug;
    private String name;
    private String qualificationCode;
    private String seta;
    private Integer nqfLevel;
    private Integer durationMonths;
    private Integer stipend;
    private String location;
    private String intake;
    private LocalDate closingDate;
    private String description;
    /** The admin's requirements text, one entry per non-blank line. */
    private List<String> requirements;
}
