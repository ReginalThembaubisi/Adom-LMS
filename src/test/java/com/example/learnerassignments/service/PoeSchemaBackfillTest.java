package com.example.learnerassignments.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backfill, which writes to rows that already exist in production.
 *
 * Built to the same rules as the file migration: it says what it did on every run including
 * the run that does nothing, and running it twice changes nothing the second time.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PoeSchemaBackfillTest {

    @Autowired PoeSchemaBackfill backfill;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired ModuleFileRepository moduleFileRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private SubmissionSession session;
    private Learner learner;
    private Module module;

    @BeforeEach
    void seed() {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("Backfill Fixture").createdAt(LocalDateTime.now()).build());
        Category category = categoryRepository.save(Category.builder()
                .categoryType("CORE").learnership(learnership).build());
        module = moduleRepository.save(Module.builder().moduleName("M").moduleCode("M1")
                .category(category).createdAt(LocalDateTime.now()).build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("T").description("").dueDate(LocalDateTime.now().plusDays(1))
                .module(module).createdAt(LocalDateTime.now()).build());
        session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("W").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(1))
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now()).build());
        learner = learnerRepository.save(Learner.builder()
                .learnerCode("2026000" + (System.nanoTime() % 100)).fullName("L")
                .learnership(learnership).passwordHash(passwordEncoder.encode("x"))
                .modules(new HashSet<>()).createdAt(LocalDateTime.now()).build());
    }

    /** A row as it exists before this phase: the new columns are null. */
    private Submission legacySubmission(String feedback, String markedPath) {
        Submission s = submissionRepository.save(Submission.builder()
                .learner(learner).session(session).filePath("/tmp/x.pdf").originalFilename("x.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .feedback(feedback).markedFilePath(markedPath)
                .build());
        s.setFeedbackStatus(null);
        s.setFeedbackVisibility(null);
        return s;
    }

    private ModuleFile legacyModuleFile() {
        ModuleFile f = moduleFileRepository.save(ModuleFile.builder()
                .module(module).title("Guide").filePath("/tmp/guide.pdf")
                .originalFilename("guide.pdf").fileType("Syllabus")
                .createdAt(LocalDateTime.now()).build());
        f.setPoeSection(null);
        f.setVersion(null);
        f.setCurrent(null);
        return f;
    }

    @Test
    @DisplayName("work that was already marked stays visible, rather than being retracted as a draft")
    void alreadyMarkedWorkBecomesPublished() {
        Submission marked = legacySubmission("Good structure, competent.", null);
        Submission withMarkedCopy = legacySubmission(null, "https://res.cloudinary.com/x/marked.pdf");

        backfill.run();

        assertThat(marked.getFeedbackStatus())
                .as("a learner has already read this feedback; calling it a draft would take it away")
                .isEqualTo(FeedbackStatus.PUBLISHED);
        assertThat(withMarkedCopy.getFeedbackStatus()).isEqualTo(FeedbackStatus.PUBLISHED);
    }

    @Test
    @DisplayName("unmarked work becomes a draft, since there is nothing to publish")
    void unmarkedWorkBecomesDraft() {
        Submission unmarked = legacySubmission(null, null);
        Submission blankFeedback = legacySubmission("   ", null);

        backfill.run();

        assertThat(unmarked.getFeedbackStatus()).isEqualTo(FeedbackStatus.DRAFT);
        assertThat(blankFeedback.getFeedbackStatus())
                .as("whitespace is not feedback")
                .isEqualTo(FeedbackStatus.DRAFT);
    }

    @Test
    @DisplayName("visibility defaults to the learner")
    void visibilityDefaultsToLearner() {
        Submission s = legacySubmission("Some feedback", null);

        backfill.run();

        assertThat(s.getFeedbackVisibility()).isEqualTo(FeedbackVisibility.LEARNER);
    }

    @Test
    @DisplayName("module files land in section 3, version 1, current")
    void moduleFilesGetTheirDefaults() {
        ModuleFile f = legacyModuleFile();

        backfill.run();

        assertThat(f.getPoeSection()).isEqualTo(3);
        assertThat(f.getVersion()).isEqualTo(1);
        assertThat(f.getCurrent()).isTrue();
    }

    @Test
    @DisplayName("running it again changes nothing it has already decided")
    void isIdempotent() {
        Submission s = legacySubmission("Marked work", null);
        ModuleFile f = legacyModuleFile();

        backfill.run();
        assertThat(s.getFeedbackStatus()).isEqualTo(FeedbackStatus.PUBLISHED);

        // Someone then does something the backfill must not undo: retracts the feedback, and
        // supersedes the guide with a newer version.
        s.setFeedbackStatus(FeedbackStatus.DRAFT);
        f.setCurrent(false);
        f.setVersion(2);

        backfill.run();

        assertThat(s.getFeedbackStatus())
                .as("a second run must not reinstate its own first answer over a later decision")
                .isEqualTo(FeedbackStatus.DRAFT);
        assertThat(f.getCurrent()).isFalse();
        assertThat(f.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("a run with nothing to do still says so")
    void noOpStillReports() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(PoeSchemaBackfill.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            backfill.run();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .as("absence of this line after a deploy must mean the backfill did not run")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Portfolio of Evidence backfill complete");
                });
    }

    @Test
    @DisplayName("hashes are not invented for files that predate the column")
    void doesNotBackfillHashes() {
        Submission s = legacySubmission("Marked work", "https://res.cloudinary.com/x/marked.pdf");
        ModuleFile f = legacyModuleFile();

        backfill.run();

        // Hashing these would mean re-downloading them, and a hash of a file fetched now says
        // only what that file is now — the opposite of what a signature needs.
        assertThat(s.getSha256()).isNull();
        assertThat(s.getMarkedSha256()).isNull();
        assertThat(f.getSha256()).isNull();
    }
}
