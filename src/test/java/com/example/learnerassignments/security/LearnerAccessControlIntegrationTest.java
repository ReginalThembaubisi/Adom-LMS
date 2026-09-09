package com.example.learnerassignments.security;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 ownership tests.
 *
 * Two learners, both with a submission on the same module, because that is the shape that
 * catches an unscoped query: a repository method missing its learner filter still returns
 * the right answer when only one learner has data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnerAccessControlIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LecturerRepository lecturerRepository;

    private static final String PASSWORD = "correct-horse";

    private Learner learnerA;
    private Learner learnerB;
    private Submission submissionA;
    private Submission submissionB;
    private Module module;

    @BeforeEach
    void seedCohort() throws IOException {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("MICT SETA Systems Development")
                .qualificationCode("NQF5")
                .createdAt(LocalDateTime.now())
                .build());

        Lecturer lecturer = lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Owner")
                .email("owner@example.com")
                .username("owner-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("lecturer123"))
                .createdAt(LocalDateTime.now())
                .build());

        Category category = categoryRepository.save(Category.builder()
                .categoryType("CORE")
                .learnership(learnership)
                .lecturer(lecturer)
                .build());

        module = moduleRepository.save(Module.builder()
                .moduleName("Systems Analysis")
                .moduleCode("SA101")
                .category(category)
                .createdAt(LocalDateTime.now())
                .build());

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Analysis Report")
                .description("Write it up")
                .dueDate(LocalDateTime.now().plusDays(7))
                .module(module)
                .createdAt(LocalDateTime.now())
                .build());

        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Window 1")
                .assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());

        learnerA = createLearner("202600001", "Amanda Randy Mndawe", module);
        learnerB = createLearner("202600002", "Bongani Sithole", module);

        // Both learners submit to the same session and both are marked, so a query that
        // forgets to filter by learner still returns something plausible.
        submissionA = createSubmission(learnerA, session, "amanda-report.pdf", "Good structure, competent.");
        submissionB = createSubmission(learnerB, session, "bongani-report.pdf", "Needs more detail.");
    }

    private Learner createLearner(String code, String fullName, Module enrolledOn) {
        Set<Module> modules = new HashSet<>();
        modules.add(enrolledOn);
        return learnerRepository.save(Learner.builder()
                .learnerCode(code)
                .fullName(fullName)
                .email(code + "@example.com")
                .cohort("2026-01")
                .learnership(enrolledOn.getCategory().getLearnership())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .modules(modules)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Submission createSubmission(Learner learner, SubmissionSession session, String filename, String feedback)
            throws IOException {
        Path file = Files.createTempFile("poe-test-", ".pdf");
        Files.writeString(file, "%PDF-1.4 test fixture for " + filename);
        file.toFile().deleteOnExit();

        return submissionRepository.save(Submission.builder()
                .learner(learner)
                .session(session)
                .filePath(file.toAbsolutePath().toString())
                .originalFilename(filename)
                .submittedAt(LocalDateTime.now())
                .status(SubmissionStatus.SUBMITTED)
                .feedback(feedback)
                .gradedAt(LocalDateTime.now())
                .gradedByRole("LECTURER")
                .gradedByName("Prof. Owner")
                .marksAwarded(80)
                .build());
    }

    /** Logs in over the real endpoint and returns the bearer token. */
    private String login(String learnerCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/learners/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learnerCode + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // --- Authentication ---

    @Test
    @DisplayName("unauthenticated request to /api/me/** is rejected with 401")
    void unauthenticatedMeRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/modules")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/timeline")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is not a session")
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/submissions").header("Authorization", bearer("not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logging out revokes the token immediately")
    void logoutRevokesTheToken() throws Exception {
        String token = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/me/submissions").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/me/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/submissions").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // --- Ownership ---

    @Test
    @DisplayName("a learner's submission list contains only their own work")
    void submissionsAreScopedToTheAuthenticatedLearner() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/me/submissions").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].submissionId").value(submissionA.getId()))
                .andExpect(jsonPath("$[0].originalFilename").value("amanda-report.pdf"))
                .andExpect(jsonPath("$[0].feedback").value("Good structure, competent."));

        String tokenB = login(learnerB.getLearnerCode());

        mockMvc.perform(get("/api/me/submissions").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].submissionId").value(submissionB.getId()))
                .andExpect(jsonPath("$[0].feedback").value("Needs more detail."));
    }

    @Test
    @DisplayName("learner A cannot read learner B's submission file — 404, not 403")
    void crossLearnerFileAccessIsNotFound() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/submissions/" + submissionB.getId() + "/view")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/submissions/" + submissionB.getId() + "/grading-history")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/submissions/" + submissionB.getId() + "/annotations")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a learner can read their own submission file")
    void ownFileIsReadable() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
    }

    // --- View tickets ---

    @Test
    @DisplayName("a view ticket only opens the submission it was minted for")
    void viewTicketIsScopedToOneSubmission() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        MvcResult minted = mockMvc.perform(post("/api/me/submissions/" + submissionA.getId() + "/view-ticket")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andReturn();
        String ticket = objectMapper.readTree(minted.getResponse().getContentAsString()).get("ticket").asText();

        // Works, with no Authorization header at all — this is the iframe path.
        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view").param("ticket", ticket))
                .andExpect(status().isOk());

        // Replaying it against someone else's submission does not.
        mockMvc.perform(get("/api/submissions/" + submissionB.getId() + "/view").param("ticket", ticket))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a learner cannot mint a ticket for someone else's submission")
    void ticketForAnotherLearnersSubmissionIsNotFound() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(post("/api/me/submissions/" + submissionB.getId() + "/view-ticket")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a forged ticket is rejected")
    void forgedTicketIsRejected() throws Exception {
        String forged = learnerB.getId() + ":" + submissionB.getId() + ":"
                + (System.currentTimeMillis() / 1000 + 600) + ":not-a-real-signature";

        mockMvc.perform(get("/api/submissions/" + submissionB.getId() + "/view").param("ticket", forged))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the submission file endpoint is closed to anonymous callers")
    void anonymousCannotReadSubmissionFile() throws Exception {
        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view"))
                .andExpect(status().isNotFound());
    }

    // --- Scoping of the rest of the portal ---

    @Test
    @DisplayName("module detail is limited to modules the learner is enrolled on")
    void moduleDetailRequiresEnrolment() throws Exception {
        Learnership other = learnershipRepository.save(Learnership.builder()
                .name("Unrelated Learnership")
                .createdAt(LocalDateTime.now())
                .build());
        Category otherCategory = categoryRepository.save(Category.builder()
                .categoryType("ELECTIVE")
                .learnership(other)
                .build());
        Module otherModule = moduleRepository.save(Module.builder()
                .moduleName("Not Yours")
                .moduleCode("NY101")
                .category(otherCategory)
                .createdAt(LocalDateTime.now())
                .build());

        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/me/modules/" + module.getId()).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleName").value("Systems Analysis"));

        mockMvc.perform(get("/api/me/modules/" + otherModule.getId()).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a learner cannot open a thread with a facilitator who does not teach them")
    void messagingIsLimitedToOwnFacilitators() throws Exception {
        Lecturer stranger = lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Stranger")
                .email("stranger@example.com")
                .username("stranger-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("lecturer123"))
                .createdAt(LocalDateTime.now())
                .build());

        String tokenA = login(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/me/messages/" + stranger.getId()).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    // --- The routes this phase closed ---

    @Test
    @DisplayName("the old learner-code routes no longer serve anyone's data")
    void legacyLearnerCodeRoutesAreClosed() throws Exception {
        String code = learnerA.getLearnerCode();

        mockMvc.perform(get("/api/learners/" + code + "/submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learners/" + code + "/modules")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learners/" + code + "/timeline")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learners/" + code + "/messages")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learners/" + code)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the roster is staff-only")
    void rosterIsStaffOnly() throws Exception {
        mockMvc.perform(get("/api/learners")).andExpect(status().isUnauthorized());

        String tokenA = login(learnerA.getLearnerCode());
        mockMvc.perform(get("/api/learners").header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a learner reads their own profile from the session, not from a code")
    void profileComesFromTheSession() throws Exception {
        String tokenB = login(learnerB.getLearnerCode());

        mockMvc.perform(get("/api/me").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerCode").value(learnerB.getLearnerCode()))
                .andExpect(jsonPath("$.fullName").value("Bongani Sithole"));
    }
}
