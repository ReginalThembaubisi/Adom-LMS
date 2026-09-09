package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.*;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.SenderType;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.security.CurrentLearner;
import com.example.learnerassignments.security.LearnerPrincipal;
import com.example.learnerassignments.security.LearnerTokenService;
import com.example.learnerassignments.security.ViewTicketService;
import com.example.learnerassignments.service.ChatbotService;
import com.example.learnerassignments.service.LearnerService;
import com.example.learnerassignments.service.MessageService;
import com.example.learnerassignments.service.ModuleService;
import com.example.learnerassignments.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Everything a signed-in learner can reach.
 *
 * The learner is always taken from the authenticated principal. No method on this
 * controller accepts a learner code, id or name as an argument — the caller cannot name
 * whose record they want, only ask for their own.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentLearner currentLearner;
    private final LearnerService learnerService;
    private final ModuleService moduleService;
    private final MessageService messageService;
    private final SubmissionService submissionService;
    private final ChatbotService chatbotService;
    private final LearnerTokenService tokenService;
    private final ViewTicketService viewTicketService;
    private final LecturerRepository lecturerRepository;

    // --- Identity ---

    @GetMapping
    public ResponseEntity<LearnerResponse> getProfile() {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(learnerService.getLearnerByCode(principal.learnerCode()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            tokenService.revoke(header.substring("Bearer ".length()).trim());
        }
        return ResponseEntity.noContent().build();
    }

    // --- Coursework ---

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleResponseDto>> getModules() {
        return ResponseEntity.ok(moduleService.getEnrolledModules(currentLearner.require().learnerCode()));
    }

    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ModuleDetailResponseDto> getModuleDetails(@PathVariable Long moduleId) {
        LearnerPrincipal principal = currentLearner.require();
        return ResponseEntity.ok(moduleService.getModuleDetailsForLearner(moduleId, principal.learnerCode()));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TimelineResponseDto>> getTimeline() {
        return ResponseEntity.ok(moduleService.getLearnerTimeline(currentLearner.require().learnerCode()));
    }

    // --- Submissions ---

    @GetMapping("/submissions")
    public ResponseEntity<List<StudentSubmissionHistoryDto>> getSubmissions() {
        return ResponseEntity.ok(learnerService.getLearnerSubmissions(currentLearner.require().learnerCode()));
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submitAssignment(
            @RequestParam("session_id") Long sessionId,
            @RequestParam("file") MultipartFile file) {

        LearnerPrincipal principal = currentLearner.require();
        SubmissionResponse response = submissionService.submitAssignment(principal.learnerCode(), sessionId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mints a short-lived ticket for reading one of the learner's own submission files.
     *
     * The portal renders documents in an iframe, which cannot carry an Authorization header.
     * Rather than putting a durable credential in the URL — which is what the old learnerCode
     * query parameter did — the ownership check happens here, once, and the browser gets a
     * signed ticket scoped to this one submission for a few minutes.
     */
    @PostMapping("/submissions/{submissionId}/view-ticket")
    public ResponseEntity<ViewTicketResponse> issueViewTicket(@PathVariable Long submissionId) {
        LearnerPrincipal principal = currentLearner.require();
        submissionService.requireOwnedByLearner(submissionId, principal.learnerId());

        ViewTicketService.Ticket ticket = viewTicketService.issue(principal.learnerId(), submissionId);
        return ResponseEntity.ok(ViewTicketResponse.builder()
                .ticket(ticket.value())
                .expiresAtEpochSecond(ticket.expiresAtEpochSecond())
                .build());
    }

    // --- Messages ---

    @GetMapping("/facilitators")
    public ResponseEntity<List<PersonSummaryDto>> getFacilitators() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(messageService.getFacilitatorsForLearner(learner));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageThreadSummaryDto>> getThreads() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(messageService.getThreadSummariesForLearner(learner));
    }

    @GetMapping("/messages/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Learner learner = currentLearner.requireLearner();
        return ResponseEntity.ok(Map.of("unreadCount", messageService.getUnreadCountForLearner(learner)));
    }

    @GetMapping("/messages/{lecturerId}")
    public ResponseEntity<List<MessageDto>> getThread(@PathVariable Long lecturerId) {
        Learner learner = currentLearner.requireLearner();
        requireOwnFacilitator(lecturerId, learner);
        return ResponseEntity.ok(messageService.getThreadAndMarkRead(learner.getId(), lecturerId, SenderType.LEARNER));
    }

    @PostMapping("/messages/{lecturerId}")
    public ResponseEntity<MessageDto> sendMessage(@PathVariable Long lecturerId,
                                                  @Valid @RequestBody SendMessageRequest request) {
        Learner learner = currentLearner.requireLearner();
        Lecturer lecturer = requireOwnFacilitator(lecturerId, learner);
        MessageDto saved = messageService.sendMessage(learner, lecturer, SenderType.LEARNER, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // --- Assistant ---

    @PostMapping("/chatbot/ask")
    public ResponseEntity<Map<String, String>> askChatbot(@RequestBody Map<String, String> payload) {
        LearnerPrincipal principal = currentLearner.require();
        String query = payload.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("response", "Please ask a question."));
        }
        // The learner code comes from the session, not the request body — the assistant
        // answers with deadlines and submission state, which is one learner's own record.
        return ResponseEntity.ok(Map.of("response", chatbotService.generateResponse(query, principal.learnerCode())));
    }

    /**
     * A facilitator who teaches one of this learner's modules, or 404.
     *
     * Reported as not found rather than forbidden so the response cannot be used to probe
     * which lecturer ids exist.
     */
    private Lecturer requireOwnFacilitator(Long lecturerId, Learner learner) {
        if (!messageService.facilitatorOwnsLearner(lecturerId, learner)) {
            throw new ResourceNotFoundException("Facilitator not found");
        }
        return lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Facilitator not found"));
    }
}
