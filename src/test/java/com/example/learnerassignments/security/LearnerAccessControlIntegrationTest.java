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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @MockBean JavaMailSender mailSender;

    private static final String PASSWORD = "correct-horse";

    private Learner learnerA;
    private Learner learnerB;
    private Submission submissionA;
    private Submission submissionB;
    private Module module;
    private SubmissionSession openSession;

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

        openSession = sessionRepository.save(SubmissionSession.builder()
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
        submissionA = createSubmission(learnerA, openSession, "amanda-report.pdf", "Good structure, competent.");
        submissionB = createSubmission(learnerB, openSession, "bongani-report.pdf", "Needs more detail.");
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

    /**
     * The reset flow is the one path a locked-out learner has, and this phase changed it:
     * resetting now revokes every session opened with the old password. It also carries the
     * whole cohort on deploy day, since everyone is signed out once when the stored session
     * shape changes.
     */
    @Test
    @DisplayName("resetting a password issues a working login and kills the old session")
    void passwordResetWorksAndRevokesExistingSessions() throws Exception {
        String oldToken = login(learnerA.getLearnerCode());
        mockMvc.perform(get("/api/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/learners/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learnerA.getLearnerCode() + "\","
                                + "\"email\":\"" + learnerA.getEmail() + "\"}"))
                .andExpect(status().isOk());

        String resetCode = learnerRepository.findByLearnerCode(learnerA.getLearnerCode())
                .orElseThrow().getResetCode();
        assertThat(resetCode).as("a reset code must have been stored to send").isNotBlank();

        mockMvc.perform(post("/api/learners/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learnerA.getLearnerCode() + "\","
                                + "\"resetCode\":\"" + resetCode + "\","
                                + "\"newPassword\":\"a-brand-new-password\"}"))
                .andExpect(status().isOk());

        // Whoever held the old session is out, including an attacker the reset was prompted by.
        mockMvc.perform(get("/api/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(post("/api/learners/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learnerA.getLearnerCode() + "\","
                                + "\"password\":\"a-brand-new-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String newToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        mockMvc.perform(get("/api/me").header("Authorization", bearer(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerCode").value(learnerA.getLearnerCode()));
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

    @Test
    @DisplayName("the submission file endpoint is closed to anonymous callers")
    void anonymousCannotReadSubmissionFile() throws Exception {
        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a learner cannot submit work attributed to another learner")
    void submissionIsAttributedToTheAuthenticatedLearner() throws Exception {
        String tokenA = login(learnerA.getLearnerCode());

        MockMultipartFile file = new MockMultipartFile(
                "file", "attempt.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes());

        // learner_code is sent deliberately: it is what the old endpoint used to decide whose
        // submission this was. The endpoint must ignore it entirely and attribute the work to
        // the session that presented the token.
        mockMvc.perform(multipart("/api/me/submissions")
                        .file(file)
                        .param("session_id", String.valueOf(openSession.getId()))
                        .param("learner_code", learnerB.getLearnerCode())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.learnerCode").value(learnerA.getLearnerCode()))
                .andExpect(jsonPath("$.learnerName").value("Amanda Randy Mndawe"));

        // And it is genuinely stored against A, not merely reported that way.
        assertThat(submissionRepository.findByLearner_LearnerCodeOrderBySubmittedAtDesc(learnerB.getLearnerCode()))
                .as("learner B must have gained nothing from A's request")
                .hasSize(1)
                .allSatisfy(s -> assertThat(s.getId()).isEqualTo(submissionB.getId()));
    }

    @Test
    @DisplayName("the legacy public uploads directory no longer serves files to anonymous callers")
    void legacyUploadsDirectoryRequiresAuthentication() throws Exception {
        // Learner submissions used to be written into this statically-served directory under
        // names built from the learner code and session id. They have been moved out, and the
        // path is closed — so a future WebConfig or storage change that puts private files
        // back here cannot silently re-expose them.
        mockMvc.perform(get("/uploads/202600001_1_amanda-report.pdf"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/uploads/"))
                .andExpect(status().isUnauthorized());
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
