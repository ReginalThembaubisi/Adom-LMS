package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ApplicationDtos.*;
import com.example.learnerassignments.dto.LearnershipAdvertDto;
import com.example.learnerassignments.security.PublicRateLimiter;
import com.example.learnerassignments.service.ApplicationService;
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
 * What the public website calls: the list of open learnerships, the application form, and
 * the status check. No sign-in on any of it, so each write is rate limited per caller and
 * nothing here returns another applicant's data or anything staff-only.
 */
@RestController
@RequiredArgsConstructor
public class PublicApplicationController {

    private final LearnershipService learnershipService;
    private final ApplicationService applicationService;
    private final PublicRateLimiter rateLimiter;

    /** Learnerships currently taking applications, soonest closing first. */
    @GetMapping("/api/learnerships/openings")
    public List<LearnershipAdvertDto> openings() {
        return learnershipService.openAdverts();
    }

    /** One open learnership by its web address (slug) or id. 404 once it stops taking applications. */
    @GetMapping("/api/learnerships/openings/{slugOrId}")
    public LearnershipAdvertDto opening(@PathVariable String slugOrId) {
        return learnershipService.openAdvert(slugOrId);
    }

    /**
     * Submits an application. A multipart form: the fields of {@link SubmitRequest}, plus the
     * files {@code idCopy} (required), {@code results} and {@code cv}.
     */
    @PostMapping(value = "/api/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitResponse> submit(@Valid @ModelAttribute SubmitRequest request,
                                                 @RequestPart(value = "idCopy", required = false) MultipartFile idCopy,
                                                 @RequestPart(value = "results", required = false) MultipartFile results,
                                                 @RequestPart(value = "cv", required = false) MultipartFile cv,
                                                 HttpServletRequest http) {
        rateLimiter.check(http, "apply", 5, Duration.ofHours(1));
        SubmitResponse response = applicationService.submit(request, idCopy, results, cv,
                PublicRateLimiter.clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** An applicant checks their own status with their reference and ID number. */
    @PostMapping("/api/applications/status")
    public StatusLookupResponse status(@Valid @RequestBody StatusLookupRequest request, HttpServletRequest http) {
        rateLimiter.check(http, "status", 20, Duration.ofMinutes(15));
        return applicationService.lookupStatus(request);
    }
}
