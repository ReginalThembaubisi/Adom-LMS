package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ApplicationDtos.*;
import com.example.learnerassignments.dto.LearnershipAdvertDto;
import com.example.learnerassignments.security.PublicRateLimiter;
import com.example.learnerassignments.model.PoeDocumentType;
import com.example.learnerassignments.service.ApplicationService;
import com.example.learnerassignments.service.ApplicationService.Upload;
import com.example.learnerassignments.service.AppointmentService;
import com.example.learnerassignments.service.OpeningService;
import com.example.learnerassignments.dto.OpeningDtos.PublicOpeningDto;
import com.example.learnerassignments.service.LearnershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * What the public website calls: the list of open learnerships, the application form, the
 * status check and the Services page's appointment requests. No sign-in on any of it, so each write is rate limited per caller and
 * nothing here returns another applicant's data or anything staff-only.
 */
@RestController
@RequiredArgsConstructor
public class PublicApplicationController {

    private final LearnershipService learnershipService;
    private final ApplicationService applicationService;
    private final AppointmentService appointmentService;
    private final OpeningService openingService;
    private final PublicRateLimiter rateLimiter;

    /** Learnerships currently taking applications, soonest closing first. */
    @GetMapping("/api/learnerships/openings")
    public List<LearnershipAdvertDto> openings() {
        return learnershipService.openAdverts();
    }

    /** Job and internship openings taking applications, for the Careers page. */
    @GetMapping("/api/openings")
    public List<PublicOpeningDto> jobOpenings() {
        return openingService.openOpenings();
    }

    /** One open learnership by its web address (slug) or id. 404 once it stops taking applications. */
    @GetMapping("/api/learnerships/openings/{slugOrId}")
    public LearnershipAdvertDto opening(@PathVariable String slugOrId) {
        return learnershipService.openAdvert(slugOrId);
    }

    /**
     * Submits an application. A multipart form: the fields of {@link SubmitRequest}, plus the
     * files below. {@code idCopy} is required; which of the rest a form sends depends on what
     * the applicant is applying for.
     */
    @PostMapping(value = "/api/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitResponse> submit(@Valid @ModelAttribute SubmitRequest request,
                                                 @RequestPart(value = "idCopy", required = false) MultipartFile idCopy,
                                                 @RequestPart(value = "results", required = false) MultipartFile results,
                                                 @RequestPart(value = "cv", required = false) MultipartFile cv,
                                                 @RequestPart(value = "transcript", required = false) MultipartFile transcript,
                                                 @RequestPart(value = "registration", required = false) MultipartFile registration,
                                                 @RequestPart(value = "placementLetter", required = false) MultipartFile placementLetter,
                                                 @RequestPart(value = "qualification", required = false) MultipartFile qualification,
                                                 @RequestPart(value = "other", required = false) MultipartFile other,
                                                 HttpServletRequest http) {
        rateLimiter.check(http, "apply", 5, Duration.ofHours(1));
        List<Upload> uploads = List.of(
                new Upload(PoeDocumentType.ID_COPY, null, idCopy),
                new Upload(PoeDocumentType.MATRIC, "School results", results),
                new Upload(PoeDocumentType.CV, null, cv),
                new Upload(PoeDocumentType.OTHER, "Academic record", transcript),
                new Upload(PoeDocumentType.OTHER, "Proof of registration", registration),
                new Upload(PoeDocumentType.OTHER, "University placement letter", placementLetter),
                new Upload(PoeDocumentType.OTHER, "Highest qualification", qualification),
                new Upload(PoeDocumentType.OTHER, "Other document", other));
        SubmitResponse response = applicationService.submit(request, uploads, PublicRateLimiter.clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** An appointment request from the Services page. */
    @PostMapping("/api/appointments")
    public ResponseEntity<Void> requestAppointment(@Valid @RequestBody AppointmentSubmitRequest request,
                                                   HttpServletRequest http) {
        rateLimiter.check(http, "appointment", 5, Duration.ofHours(1));
        appointmentService.submit(request, PublicRateLimiter.clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** An applicant checks their own status with their reference and ID number. */
    @PostMapping("/api/applications/status")
    public StatusLookupResponse status(@Valid @RequestBody StatusLookupRequest request, HttpServletRequest http) {
        rateLimiter.check(http, "status", 20, Duration.ofMinutes(15));
        return applicationService.lookupStatus(request);
    }
}
