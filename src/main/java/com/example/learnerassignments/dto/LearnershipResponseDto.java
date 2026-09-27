package com.example.learnerassignments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A learnership as staff see it. The advert fields and counts are only filled in by the admin
 * endpoints; the legacy public list sets id, name and qualificationCode alone and the rest is
 * left out of the JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LearnershipResponseDto {
    private Long id;
    private String name;
    private String qualificationCode;

    private String slug;
    private String status;
    private String seta;
    private Integer nqfLevel;
    private Integer durationMonths;
    private Integer stipend;
    private String location;
    private String intake;
    private LocalDate closingDate;
    private Integer maxLearners;
    private String description;
    private String requirements;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** True when the advert is Open and its closing date has not passed. */
    private Boolean acceptingApplications;
    /** Applications per status name, e.g. {"SUBMITTED": 12, "SHORTLISTED": 3}. */
    private Map<String, Long> applicationCounts;
    private Long applicationTotal;
}
