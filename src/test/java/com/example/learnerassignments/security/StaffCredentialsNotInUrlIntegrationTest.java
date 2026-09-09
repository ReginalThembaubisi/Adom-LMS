package com.example.learnerassignments.security;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff read submissions with an Authorization header and nothing else.
 *
 * The endpoints used to accept the same base64 credentials as an ?authToken query parameter,
 * which put lecturer and admin passwords into access logs, browser history and referrer
 * headers — and into Google's request logs, because the Word preview asked Google's document
 * viewer to fetch that URL. These assert that the header still works and the parameter is
 * inert, so the old call sites fail loudly rather than continuing to leak quietly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffCredentialsNotInUrlIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LecturerRepository lecturerRepository;
    @Autowired AdminRepository adminRepository;

    private static final String LECTURER_PASSWORD = "lecturer123";
    private static final String ADMIN_PASSWORD = "admin123";

    private Submission submission;
    private String owningLecturerUsername;
    private String otherLecturerUsername;
    private String adminUsername;

    @BeforeEach
    void seed() throws IOException {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("Credential Fixture").createdAt(LocalDateTime.now()).build());

        owningLecturerUsername = "owner-" + System.nanoTime();
        Lecturer owningLecturer = lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Owner").email("owner@example.com")
                .username(owningLecturerUsername)
                .passwordHash(passwordEncoder.encode(LECTURER_PASSWORD))
                .createdAt(LocalDateTime.now()).build());

        otherLecturerUsername = "other-" + System.nanoTime();
        lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Elsewhere").email("elsewhere@example.com")
                .username(otherLecturerUsername)
                .passwordHash(passwordEncoder.encode(LECTURER_PASSWORD))
                .createdAt(LocalDateTime.now()).build());

        adminUsername = "admin-" + System.nanoTime();
        adminRepository.save(Admin.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .createdAt(LocalDateTime.now()).build());

        Category category = categoryRepository.save(Category.builder()
                .categoryType("CORE").learnership(learnership).lecturer(owningLecturer).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("Systems Analysis").moduleCode("SA101")
                .category(category).createdAt(LocalDateTime.now()).build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Analysis Report").description("").dueDate(LocalDateTime.now().plusDays(7))
                .module(module).createdAt(LocalDateTime.now()).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Window 1").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now()).build());

        Learner learner = learnerRepository.save(Learner.builder()
                .learnerCode("202600050").fullName("Fixture Learner")
                .learnership(learnership).passwordHash(passwordEncoder.encode("x"))
                .modules(new HashSet<>()).createdAt(LocalDateTime.now()).build());

        Path file = Files.createTempFile("staff-cred-", ".pdf");
        Files.writeString(file, "%PDF-1.4 fixture");
        file.toFile().deleteOnExit();

        submission = submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath(file.toAbsolutePath().toString()).originalFilename("report.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED).build());
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String rawBase64(String username, String password) {
        return Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String viewUrl() {
        return "/api/submissions/" + submission.getId() + "/view";
    }

    @Test
    @DisplayName("the module's lecturer reads the file with an Authorization header")
    void owningLecturerReadsWithHeader() throws Exception {
        mockMvc.perform(get(viewUrl())
                        .header("Authorization", basic(owningLecturerUsername, LECTURER_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin reads the file with an Authorization header")
    void adminReadsWithHeader() throws Exception {
        mockMvc.perform(get(viewUrl())
                        .header("Authorization", basic(adminUsername, ADMIN_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a lecturer who does not teach the module is refused")
    void unrelatedLecturerIsRefused() throws Exception {
        mockMvc.perform(get(viewUrl())
                        .header("Authorization", basic(otherLecturerUsername, LECTURER_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("valid credentials in the query string grant nothing")
    void authTokenParameterIsInert() throws Exception {
        // The exact value the dashboards used to send, on the account that is allowed to read
        // this record. It must fail — otherwise the parameter is still live and the fix is
        // cosmetic.
        mockMvc.perform(get(viewUrl())
                        .param("authToken", rawBase64(owningLecturerUsername, LECTURER_PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(viewUrl())
                        .param("authToken", basic(adminUsername, ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the query string grants nothing on the other read endpoints either")
    void authTokenParameterIsInertEverywhere() throws Exception {
        String token = rawBase64(adminUsername, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/submissions/" + submission.getId() + "/grading-history")
                        .param("authToken", token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/submissions/" + submission.getId() + "/annotations")
                        .param("authToken", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unmarked submission reports no content, not 'not found'")
    void unmarkedSubmissionIsNotReportedMissing() throws Exception {
        // The record exists and this grader may read it; it simply has no strokes saved yet.
        // Answering 404 here said the submission did not exist, and made "nothing marked yet"
        // indistinguishable from "not yours" on an endpoint where that distinction is the
        // security signal.
        mockMvc.perform(get("/api/submissions/" + submission.getId() + "/annotations")
                        .header("Authorization", basic(adminUsername, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("an anonymous request is refused before it reaches the record")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get(viewUrl())).andExpect(status().isUnauthorized());
    }
}
