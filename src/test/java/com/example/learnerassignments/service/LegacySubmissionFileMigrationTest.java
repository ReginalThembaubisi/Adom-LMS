package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closing the hole for new uploads left every file collected before the change sitting in
 * the publicly served directory. These cover the migration that moves them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LegacySubmissionFileMigrationTest {

    @Autowired LegacySubmissionFileMigration migration;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ModuleRepository moduleRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionSessionRepository sessionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Value("${file.upload-dir}") String publicUploadDir;
    @Value("${file.submission-dir}") String privateSubmissionDir;

    private Path publicDir;
    private Path privateDir;
    private SubmissionSession session;
    private Learner learner;

    @BeforeEach
    void setUp() throws IOException {
        publicDir = Paths.get(publicUploadDir).toAbsolutePath().normalize();
        privateDir = Paths.get(privateSubmissionDir).toAbsolutePath().normalize();
        Files.createDirectories(publicDir);
        Files.createDirectories(privateDir);

        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("Legacy Cohort").createdAt(LocalDateTime.now()).build());
        Category category = categoryRepository.save(Category.builder()
                .categoryType("CORE").learnership(learnership).build());
        Module module = moduleRepository.save(Module.builder()
                .moduleName("Legacy Module").moduleCode("LM101")
                .category(category).createdAt(LocalDateTime.now()).build());
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .title("Legacy Task").description("").dueDate(LocalDateTime.now().plusDays(1))
                .module(module).createdAt(LocalDateTime.now()).build());
        session = sessionRepository.save(SubmissionSession.builder()
                .sessionName("Legacy Window").assignment(assignment)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(1))
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now()).build());

        learner = learnerRepository.save(Learner.builder()
                .learnerCode("202600009").fullName("Legacy Learner")
                .learnership(learnership).passwordHash(passwordEncoder.encode("x"))
                .modules(new HashSet<>()).createdAt(LocalDateTime.now()).build());
    }

    private Submission submissionStoredAt(String path) {
        return submissionRepository.save(Submission.builder()
                .learner(learner).session(session)
                .filePath(path).originalFilename("legacy.pdf")
                .submittedAt(LocalDateTime.now()).status(SubmissionStatus.SUBMITTED)
                .build());
    }

    /**
     * The point of this one is the deploy check. An operator confirming the migration ran has
     * only the log to go on, and a log that speaks up only when something changed cannot tell
     * "ran, nothing to do" apart from "never ran" — which is the failure actually worth
     * catching, since the migration is idempotent and refuses a bad directory config.
     */
    @Test
    @DisplayName("a run that finds nothing still says so, so a silent no-op is visible")
    void completionIsLoggedEvenWhenNothingMoves() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(LegacySubmissionFileMigration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            migration.run();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .as("absence of this line after a deploy must mean the migration did not run")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Legacy submission file migration complete");
                });
    }

    @Test
    @DisplayName("a submission sitting in the public uploads directory is moved out and repointed")
    void legacyFileIsMovedOutOfThePublicDirectory() throws IOException {
        Path exposed = publicDir.resolve("202600009_1_legacy.pdf");
        Files.writeString(exposed, "%PDF-1.4 collected before the fix");
        Submission submission = submissionStoredAt(exposed.toString());

        migration.run();

        assertThat(Files.exists(exposed))
                .as("the file must no longer be in the statically served directory")
                .isFalse();
        assertThat(submission.getFilePath())
                .as("the row must point at the file's new home")
                .startsWith(privateDir.toString());
        assertThat(Paths.get(submission.getFilePath()))
                .exists()
                .hasContent("%PDF-1.4 collected before the fix");
    }

    @Test
    @DisplayName("running twice moves nothing the second time")
    void migrationIsIdempotent() throws IOException {
        Path exposed = publicDir.resolve("202600009_2_legacy.pdf");
        Files.writeString(exposed, "%PDF-1.4 fixture");
        Submission submission = submissionStoredAt(exposed.toString());

        migration.run();
        String afterFirstRun = submission.getFilePath();
        migration.run();

        assertThat(submission.getFilePath()).isEqualTo(afterFirstRun);
        assertThat(Paths.get(afterFirstRun)).exists();
    }

    @Test
    @DisplayName("Cloudinary-hosted submissions are left alone")
    void remoteFilesAreUntouched() {
        String url = "https://res.cloudinary.com/demo/raw/upload/v1/legacy.pdf";
        Submission submission = submissionStoredAt(url);

        migration.run();

        assertThat(submission.getFilePath()).isEqualTo(url);
    }

    @Test
    @DisplayName("a row whose file is already gone is left as it is rather than repointed at nothing")
    void missingFileLeavesTheRowAlone() {
        String vanished = publicDir.resolve("never-existed.pdf").toString();
        Submission submission = submissionStoredAt(vanished);

        migration.run();

        assertThat(submission.getFilePath()).isEqualTo(vanished);
    }
}
