package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One submission per learner per session: uploading again before marking edits it, so a
 * double click on a slow connection cannot leave several copies for the facilitator.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubmissionResubmitTest {

    @Autowired SubmissionService submissionService;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;

    private record World(Learner learner, SubmissionSession session) {}

    private World world() {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(Learnership.builder().name("RESUB " + unique).build());
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("Resub Module").moduleCode("RS" + unique).category(category).build());
        Learner learner = learnerRepository.save(Learner.builder()
                .fullName("Resub Learner").learnerCode("RSL" + unique)
                .email("rsl" + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>(Set.of(module)))
                .build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Resub Task").description("")
                .dueDate(LocalDateTime.now().plusDays(7)).module(module).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Resub Session").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).build());
        return new World(learner, session);
    }

    private static MockMultipartFile pdf(String name, String body) {
        return new MockMultipartFile("file", name, "application/pdf", body.getBytes(StandardCharsets.UTF_8));
    }

    private List<Submission> rows(World w) {
        return submissionRepository.findByLearner_IdAndSession_IdOrderBySubmittedAtDesc(
                w.learner().getId(), w.session().getId());
    }

    @Test
    @DisplayName("Submitting twice before marking keeps one submission, holding the newer file")
    void resubmitBeforeMarkingEditsTheSubmission() {
        World w = world();
        String code = w.learner().getLearnerCode();

        var first = submissionService.submitAssignment(code, w.session().getId(), pdf("first.pdf", "%PDF one"));
        var second = submissionService.submitAssignment(code, w.session().getId(), pdf("second.pdf", "%PDF two"));

        assertThat(second.getId()).isEqualTo(first.getId());
        List<Submission> rows = rows(w);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOriginalFilename()).isEqualTo("second.pdf");
        assertThat(rows.get(0).getSha256()).isEqualTo(ContentHash.of(pdf("second.pdf", "%PDF two")));
    }

    @Test
    @DisplayName("Submitting after marking adds a new attempt and leaves the marked one untouched")
    void resubmitAfterMarkingAddsAnAttempt() {
        World w = world();
        String code = w.learner().getLearnerCode();

        var first = submissionService.submitAssignment(code, w.session().getId(), pdf("first.pdf", "%PDF one"));
        Submission marked = submissionRepository.findById(first.getId()).orElseThrow();
        marked.setGradedAt(LocalDateTime.now());
        marked.setStatus(SubmissionStatus.NOT_YET_COMPETENT);
        submissionRepository.save(marked);

        var second = submissionService.submitAssignment(code, w.session().getId(), pdf("retry.pdf", "%PDF retry"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(rows(w)).hasSize(2);
        Submission reloaded = submissionRepository.findById(first.getId()).orElseThrow();
        assertThat(reloaded.getOriginalFilename()).isEqualTo("first.pdf");
        assertThat(reloaded.getFilePath()).isNotEqualTo(submissionRepository.findById(second.getId()).orElseThrow().getFilePath());
    }
}
