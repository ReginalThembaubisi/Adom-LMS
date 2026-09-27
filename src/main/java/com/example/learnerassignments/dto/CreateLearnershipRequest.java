package com.example.learnerassignments.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Creates or updates a learnership. Only {@code name} is required; everything from
 * {@code status} down is the public advert and can be filled in later.
 */
@Data
public class CreateLearnershipRequest {
    private String name;
    private String qualificationCode;

    /** DRAFT, OPEN or CLOSED. Defaults to DRAFT on create; unchanged on update when null. */
    private String status;
    /** Optional. Generated from the name when blank. */
    private String slug;
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
}
