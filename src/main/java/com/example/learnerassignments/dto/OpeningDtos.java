package com.example.learnerassignments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Job and internship openings: what the admin writes and what the Careers page reads. */
public class OpeningDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OpeningRequest {
        @NotBlank(message = "Give the opening a title") @Size(max = 200) private String title;
        /** INTERNSHIP, GRADUATE_PROGRAMME, GRADE_12, ENTRY_LEVEL or GOVERNMENT. */
        @NotBlank(message = "Choose a category") private String category;
        @Size(max = 100) private String division;
        @Size(max = 255) private String location;
        @Min(value = 1, message = "Positions must be at least 1") @Max(10000) private Integer positions;
        private LocalDate closingDate;
        @Size(max = 10000) private String description;
        @Size(max = 5000) private String requirements;
        /** DRAFT, OPEN or CLOSED. Defaults to DRAFT on create; unchanged on update when null. */
        private String status;
    }

    /** An opening as the admin sees it, with application counts. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpeningDto {
        private Long id;
        private String title;
        private String category;
        private String categoryLabel;
        private String division;
        private String location;
        private Integer positions;
        private LocalDate closingDate;
        private String description;
        private String requirements;
        private String status;
        private Boolean acceptingApplications;
        private Map<String, Long> applicationCounts;
        private Long applicationTotal;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /** An opening as the public Careers page shows it. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PublicOpeningDto {
        private Long id;
        private String title;
        private String category;
        private String categoryLabel;
        private String division;
        private String location;
        private Integer positions;
        private LocalDate closingDate;
        private String description;
        private List<String> requirements;
    }
}
