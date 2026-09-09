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
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance criteria for Phase 1, over HTTP.
 *
 * Before this phase, hasRole("ASSESSOR") was the whole check: any assessor could read any
 * learner's work, list every module, and open or close submission windows for the cohort.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssessorModeratorScopingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AssessorRepository assessorRepository;
    @Autowired ModeratorRepository moderatorRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired LecturerRepository lecturerRepository;
    @Autowired AssessorAssignmentRepository assessorAssignmentRepository;
    @Autowired ModeratorAssignmentRepository moderatorAssignmentRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired SubmissionRepository submissionRepository;

    private static final String PW = "staff-password";

    private String assessorUser;
    private String moderatorUser;
    private String adminUser;

    private Learner learnerA;
    private Learner learnerC;
    private Submission submissionA;
    private Submission submissionC;
    private Module coreModule;
    private Module otherModule;
    private SubmissionSession session;

    @BeforeEach
    void seed() throws IOException {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("MICT SETA").createdAt(LocalDateTime.now()).build());

        Lecturer lecturer = lecturerRepository.save(Lecturer.builder()
                .fullName("Prof. Owner").username("lect-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());

        Category core = categoryRepository.save(Category.builder()
                .categoryType("CORE").learnership(learnership).lecturer(lecturer).build());
        Category elective = categoryRepository.save(Category.builder()
                .categoryType("ELECTIVE").learnership(learnership).lecturer(lecturer).build());

        coreModule = moduleRepository.save(Module.builder().moduleName("Systems Analysis")
                .moduleCode("SA101").category(core).createdAt(LocalDateTime.now()).build());
        otherModule = moduleRepository.save(Module.builder().moduleName("Elective Topic")
                .moduleCode("EL101").category(elective).createdAt(LocalDateTime.now()).build());

        learnerA = learner("202600001", "Amanda", learnership, "2026-01", coreModule);
        Learner learnerB = learner("202600002", "Bongani", learnership, "2026-01", coreModule);
        learnerC = learner("202600003", "Charmaine", learnership, "2026-02", otherModule);

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Report").description("").dueDate(LocalDateTime.now().plusDays(7))
                .module(coreModule).createdAt(LocalDateTime.now()).build());
        session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Window 1").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now()).build());

        Assignment otherAssignment = assignmentRepository.save(Assignment.builder()
                .title("Elective Task").description("").dueDate(LocalDateTime.now().plusDays(7))
                .module(otherModule).createdAt(LocalDateTime.now()).build());
        SubmissionSession otherSession = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Elective Window").assignment(otherAssignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now()).build());

        submissionA = submission(learnerA, session, "amanda.pdf");
        submission(learnerB, session, "bongani.pdf");
        submissionC = submission(learnerC, otherSession, "charmaine.pdf");

        assessorUser = "assessor-" + System.nanoTime();
        assessorRepository.save(Assessor.builder().fullName("An Assessor").username(assessorUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
        moderatorUser = "moderator-" + System.nanoTime();
        moderatorRepository.save(Moderator.builder().fullName("A Moderator").username(moderatorUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
        adminUser = "admin-" + System.nanoTime();
        adminRepository.save(Admin.builder().username(adminUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
    }

    private Learner learner(String code, String name, Learnership ls, String cohort, Module m) {
        Set<Module> modules = new HashSet<>();
        modules.add(m);
        return learnerRepository.save(Learner.builder()
                .learnerCode(code).fullName(name).learnership(ls).cohort(cohort)
                .passwordHash(passwordEncoder.encode("x")).modules(modules)
                .createdAt(LocalDateTime.now()).build());
    }

    private Submission submission(Learner l, SubmissionSession s, String filename) throws IOException {
        Path file = Files.createTempFile("scope-", ".pdf");
        Files.writeString(file, "%PDF-1.4 " + filename);
        file.toFile().deleteOnExit();
        return submissionRepository.save(Submission.builder()
                .learner(l).session(s).filePath(file.toAbsolutePath().toString())
                .originalFilename(filename).submittedAt(LocalDateTime.now())
                .status(SubmissionStatus.SUBMITTED).build());
    }

    private String basic(String user) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + PW).getBytes(StandardCharsets.UTF_8));
    }

    private void assignAssessorTo(Learner l) {
        assessorAssignmentRepository.save(AssessorAssignment.builder()
                .assessor(assessorRepository.findByUsername(assessorUser).orElseThrow())
                .learner(l).build());
    }

    // --- The default ---

    @Test
    @DisplayName("an unassigned assessor sees no modules, no sessions and no submissions")
    void unassignedAssessorSeesNothing() throws Exception {
        mockMvc.perform(get("/api/assessor/modules").header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/assessor/sessions").header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view")
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isNotFound());
    }

    // --- Acceptance criteria from the brief ---

    @Test
    @DisplayName("an assessor assigned to A and B cannot read C's submission")
    void assessorCannotReadUnassignedLearnersSubmission() throws Exception {
        assignAssessorTo(learnerA);
        assignAssessorTo(learnerRepository.findByLearnerCode("202600002").orElseThrow());

        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view")
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/submissions/" + submissionC.getId() + "/view")
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an assessor lists only the modules their assigned learners are enrolled on")
    void assessorSeesOnlyAssignedModules() throws Exception {
        assignAssessorTo(learnerA);

        mockMvc.perform(get("/api/assessor/modules").header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].moduleName").value("Systems Analysis"));

        // The staff module route in SecurityConfig is role-gated, so it needs scoping too.
        mockMvc.perform(get("/api/modules/" + otherModule.getId())
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/modules/" + coreModule.getId())
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a moderator assigned to cohort 2026-01 sees no learner outside it")
    void moderatorSeesOnlyTheirCohort() throws Exception {
        moderatorAssignmentRepository.save(ModeratorAssignment.builder()
                .moderator(moderatorRepository.findByUsername(moderatorUser).orElseThrow())
                .learnership(learnerA.getLearnership()).cohort("2026-01")
                .scope(ModerationScope.FULL).build());

        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view")
                        .header("Authorization", basic(moderatorUser)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/" + submissionC.getId() + "/view")
                        .header("Authorization", basic(moderatorUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reaching a session does not reveal learners in it who are out of scope")
    void sessionOverviewIsFilteredToAssignedLearners() throws Exception {
        assignAssessorTo(learnerA);

        // Learner B has work in this same session and was never assigned to this assessor.
        mockMvc.perform(get("/api/assessor/sessions/" + session.getId() + "/submissions")
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted.length()").value(1))
                .andExpect(jsonPath("$.submitted[0].learnerCode").value("202600001"))
                .andExpect(jsonPath("$.totalLearners").value(1));
    }

    @Test
    @DisplayName("an assessor can no longer open or close submission windows")
    void assessorCannotMutateSessions() throws Exception {
        assignAssessorTo(learnerA);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/sessions/" + session.getId() + "/close")
                        .header("Authorization", basic(assessorUser)))
                .andExpect(status().isForbidden());
    }

    // --- Admin ---

    @Test
    @DisplayName("an admin is unaffected by any of this")
    void adminIsUnscoped() throws Exception {
        mockMvc.perform(get("/api/submissions/" + submissionA.getId() + "/view")
                        .header("Authorization", basic(adminUser)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/submissions/" + submissionC.getId() + "/view")
                        .header("Authorization", basic(adminUser)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/modules/" + otherModule.getId())
                        .header("Authorization", basic(adminUser)))
                .andExpect(status().isOk());
    }
}
