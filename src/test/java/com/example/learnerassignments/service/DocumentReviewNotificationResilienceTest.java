package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LearnershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Found in production: rejecting a document came back "the database rejected that change...
 * nothing was saved" — the reviewer's own decision was being rolled back by the notification
 * write that runs alongside it, over a problem that had nothing to do with the review itself.
 *
 * {@link NotificationService} is mocked here specifically to force that failure on demand and
 * prove {@link LearnerDocumentService#review} survives it. This does not exercise the
 * {@code REQUIRES_NEW} transaction boundary that makes the fix work against a real database —
 * that needs an actual failing statement, which is what this fix was verified against in a
 * running PostgreSQL container. What this test proves instead is the other half of the fix: even
 * once a notification failure can no longer touch the review's own transaction, the exception it
 * throws still has to be caught here, or the call still fails and still reports the review as
 * lost even though it committed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentReviewNotificationResilienceTest {

    @Autowired LearnerDocumentService documentService;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;

    @MockBean NotificationService notificationService;

    private Learner learner(String tag) {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("RESILIENCE " + unique).build());
        return learnerRepository.save(Learner.builder()
                .fullName(tag + " Learner").learnerCode(tag + unique)
                .email(tag + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>()).build());
    }

    private LearnerDocument document(Learner learner) {
        return documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(PoeDocumentType.CV)
                .filePath("lms_secure/doc").originalFilename("cv.pdf")
                .version(1).current(true).status(ReviewStatus.PENDING)
                .uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER")
                .build());
    }

    @Test
    @DisplayName("A rejection still saves even when notifying the learner fails")
    void rejectionSurvivesANotificationFailure() {
        Learner learner = learner("REJ");
        LearnerDocument doc = document(learner);

        doThrow(new RuntimeException("simulated notification failure"))
                .when(notificationService).notifyLearner(any(), any(), any(), any(), any());

        LearnerDocument reviewed = documentService.review(
                doc.getId(), ReviewStatus.REJECTED, "Please resubmit a clearer scan.", "admin");

        assertThat(reviewed.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(reviewed.getReviewNote()).isEqualTo("Please resubmit a clearer scan.");

        LearnerDocument reloaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(reloaded.getReviewNote()).isEqualTo("Please resubmit a clearer scan.");

        verify(notificationService).notifyLearner(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("An acceptance is unaffected either way — it never notifies")
    void acceptanceNeverCallsNotify() {
        Learner learner = learner("ACC");
        LearnerDocument doc = document(learner);

        LearnerDocument reviewed = documentService.review(doc.getId(), ReviewStatus.ACCEPTED, null, "admin");

        assertThat(reviewed.getStatus()).isEqualTo(ReviewStatus.ACCEPTED);
        verify(notificationService, org.mockito.Mockito.never())
                .notifyLearner(any(), any(), any(), any(), any());
    }
}
