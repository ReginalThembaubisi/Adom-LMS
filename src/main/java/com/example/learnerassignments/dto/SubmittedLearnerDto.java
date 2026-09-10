package com.example.learnerassignments.dto;

import com.example.learnerassignments.model.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmittedLearnerDto {

    private Long submissionId;
    private Long learnerId;
    private String learnerCode;
    private String fullName;
    private String cohort;
    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private String originalFilename;
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
