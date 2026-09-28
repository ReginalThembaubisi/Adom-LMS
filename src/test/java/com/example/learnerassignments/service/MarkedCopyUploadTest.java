package com.example.learnerassignments.service;

import com.example.learnerassignments.exception.InvalidFileException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A grader downloads a submission, marks it somewhere else and uploads it back. While the
 * file was away the submission may have moved on; each way it can have done so is refused
 * rather than silently overwritten.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarkedCopyUploadTest {

    @Autowired SubmissionService submissionService;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;

    @MockBean CloudinaryService cloudinaryService;
    @MockBean JavaMailSender mailSender;

    private int uploads;

    @BeforeEach
    void storage() throws Exception {
        when(cloudinaryService.isConfigured()).thenReturn(true);
        when(cloudinaryService.uploadLearnerFile(any(MultipartFile.class))).thenAnswer(inv -> "marked/copy-" + (++uploads));
    }

    private record World(Learner learner, SubmissionSession session) {}

    private World world() {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(Learnership.builder().name("MARKED " + unique).build());
        Category category = categoryRepository.save(
                Category.builder().categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("Marked Module").moduleCode("MK" + unique).category(category).build());
        Learner learner = learnerRepository.save(Learner.builder()
                .fullName("Marked Learner").learnerCode("MKL" + unique)
                .email("mkl" + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>(Set.of(module)))
                .build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Marked Task").description("")
                .dueDate(LocalDateTime.now().plusDays(7)).module(module).build());
        SubmissionSession session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Marked Session").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(7))
                .status(SessionStatus.OPEN).build());
        return new World(learner, session);
    }

    private Submission submission(World w, LocalDateTime submittedAt) {
        return submissionRepository.save(Submission.builder()
                .learner(w.learner()).session(w.session())
                .filePath("original/" + System.nanoTime()).originalFilename("work.docx")
                .submittedAt(submittedAt).status(SubmissionStatus.SUBMITTED)
                .build());
    }

    private static MockMultipartFile pdf(String body) {
        return new MockMultipartFile("file", "marked.pdf", "application/pdf",
                ("%PDF-1.7 " + body).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("An uploaded marked copy is stored beside the original, which is left untouched")
    void uploadStoresMarkedCopy() throws Exception {
        World w = world();
        Submission s = submission(w, LocalDateTime.now());
        String original = s.getFilePath();

        String version = submissionService.uploadMarkedCopy(s.getId(), pdf("marks"), null, false);

        Submission reloaded = submissionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getFilePath()).isEqualTo(original);
        assertThat(reloaded.getMarkedFilePath()).isNotNull();
        assertThat(version).isEqualTo(reloaded.markedCopyVersion()).isEqualTo(ContentHash.of(pdf("marks")));
    }

    @Test
    @DisplayName("A second upload is refused when it was made against a copy someone has since replaced")
    void staleUploadIsAConflict() throws Exception {
        World w = world();
        Submission s = submission(w, LocalDateTime.now());
        String first = submissionService.uploadMarkedCopy(s.getId(), pdf("first"), null, false);

        // A second grader who opened it before the first upload still believes there is none.
        assertThatThrownBy(() -> submissionService.uploadMarkedCopy(s.getId(), pdf("second"), null, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));

        // Whoever saw the current version can replace it.
        String second = submissionService.uploadMarkedCopy(s.getId(), pdf("second"), first, false);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Marking uploaded for a submission the learner has since replaced is refused")
    void supersededSubmissionIsAConflict() {
        World w = world();
        Submission old = submission(w, LocalDateTime.now().minusHours(2));
        submission(w, LocalDateTime.now());

        assertThatThrownBy(() -> submissionService.uploadMarkedCopy(old.getId(), pdf("late"), null, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(submissionRepository.findById(old.getId()).orElseThrow().getMarkedFilePath()).isNull();
    }

    @Test
    @DisplayName("Pen marks made in the app are replaced by an upload only when the grader agrees")
    void penMarksNeedConfirmation() throws Exception {
        World w = world();
        Submission s = submission(w, LocalDateTime.now());
        submissionService.saveAnnotations(s.getId(), "{\"1\":[]}", false);

        assertThatThrownBy(() -> submissionService.uploadMarkedCopy(s.getId(), pdf("marks"), null, false))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(submissionRepository.findById(s.getId()).orElseThrow().getAnnotationsJson()).isNotNull();

        submissionService.uploadMarkedCopy(s.getId(), pdf("marks"), null, true);
        Submission reloaded = submissionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getAnnotationsJson()).isNull();
        assertThat(reloaded.getMarkedFilePath()).isNotNull();
    }

    @Test
    @DisplayName("Pen marks saved over an uploaded copy replace it only when the grader agrees")
    void penMarksOverUploadNeedConfirmation() throws Exception {
        World w = world();
        Submission s = submission(w, LocalDateTime.now());
        submissionService.uploadMarkedCopy(s.getId(), pdf("marks"), null, false);

        assertThatThrownBy(() -> submissionService.saveAnnotations(s.getId(), "{\"1\":[]}", false))
                .isInstanceOf(ResponseStatusException.class);

        submissionService.saveAnnotations(s.getId(), "{\"1\":[]}", true);
        Submission reloaded = submissionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getMarkedFilePath()).isNull();
        assertThat(reloaded.getAnnotationsJson()).isNotNull();
    }

    @Test
    @DisplayName("Only a real PDF is accepted, whatever the file is called")
    void nonPdfIsRejected() {
        World w = world();
        Submission s = submission(w, LocalDateTime.now());
        MockMultipartFile renamedWord = new MockMultipartFile("file", "marked.pdf", "application/pdf",
                new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00});

        assertThatThrownBy(() -> submissionService.uploadMarkedCopy(s.getId(), renamedWord, null, false))
                .isInstanceOf(InvalidFileException.class);
    }
}
