package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubmissionHistoryDto {

    private Long submissionId;
    private String sessionName;
    private String assignmentTitle;
    private String moduleName;
    private String originalFilename;
    private LocalDateTime submittedAt;
    private String status;
    private String feedback;
    private LocalDateTime gradedAt;
    private String gradedByRole;
    private String gradedByName;
    private Integer marksAwarded;
    /**
     * Whether a marked copy exists — not where it is.
     *
     * This used to be the stored path, which for a file uploaded before Phase 4 is a working
     * public Cloudinary URL: an unauthenticated link to somebody's assessment work, handed to
     * every client that lists a session. Access control on the view endpoint cannot undo that,
     * because once a URL is out it is out. The UI only ever asked whether a marked copy
     * existed, so that is all it is told.
     */
    private boolean hasMarkedCopy;
    private boolean hasAnnotations;
}
