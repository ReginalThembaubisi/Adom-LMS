package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A badge is only useful if it is accurate and if the things behind it are worth reading. These
 * test both: the count, and the rules that keep a notification from being noise or a hazard.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationTest {

    @Autowired NotificationService notificationService;
    @Autowired NotificationStream notificationStream;
    @Autowired LearnerDocumentService documentService;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;

    @Test
    @DisplayName("A notification never carries a link, even when the caller writes one")
    void linksAreStripped() {
        Learner learner = learner("LINK");

        Notification n = notificationService.notifyLearner(learner, NotificationType.FEEDBACK_RELEASED,
                "SESSION", 1L, "Your marking is ready. See https://adom-lms-portal.onrender.com/#/portal now.");

        // Learners were told this system never sends links. One that does teaches them to
        // click things that look exactly like a phishing message.
        assertThat(n.getBody()).doesNotContain("http");
        assertThat(n.getBody()).doesNotContain("onrender");
        assertThat(n.getBody()).contains("Your marking is ready");
    }

    @Test
    @DisplayName("The unread count is what the badge shows, and clearing it is scoped to one learner")
    void unreadCountAndMarkRead() {
        Learner mine = learner("MINE");
        Learner theirs = learner("THEIRS");
        notificationService.notifyLearner(mine, NotificationType.FEEDBACK_RELEASED, "SESSION", 1L, "a");
        notificationService.notifyLearner(mine, NotificationType.DOCUMENT_REJECTED, "DOCUMENT", 2L, "b");
        notificationService.notifyLearner(theirs, NotificationType.FEEDBACK_RELEASED, "SESSION", 1L, "c");

        assertThat(notificationService.unreadCount(mine.getId())).isEqualTo(2);

        int cleared = notificationService.markAllRead(mine.getId());

        assertThat(cleared).isEqualTo(2);
        assertThat(notificationService.unreadCount(mine.getId())).isZero();
        // Clearing mine must not clear anybody else's, or they would never know why they
        // missed their result.
        assertThat(notificationService.unreadCount(theirs.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("A learner only ever sees their own")
    void listIsScopedToTheLearner() {
        Learner mine = learner("OWN");
        Learner theirs = learner("OTHER");
        notificationService.notifyLearner(mine, NotificationType.FEEDBACK_RELEASED, "SESSION", 1L, "mine");
        notificationService.notifyLearner(theirs, NotificationType.FEEDBACK_RELEASED, "SESSION", 1L, "theirs");

        assertThat(notificationService.listFor(mine.getId()))
                .hasSize(1)
                .allSatisfy(n -> assertThat(n.getBody()).isEqualTo("mine"));
    }

    @Test
    @DisplayName("Rejecting a document tells the learner, and tells them why")
    void rejectionCarriesTheReason() {
        Learner learner = learner("REJ");
        LearnerDocument document = documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(PoeDocumentType.ID_COPY)
                .filePath("lms_secure/id").originalFilename("id.pdf")
                .status(ReviewStatus.PENDING).version(1).current(true)
                .uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER").build());

        documentService.review(document.getId(), ReviewStatus.REJECTED, "The photo page is cut off.", "Admin");

        List<Notification> notifications = notificationService.listFor(learner.getId());
        assertThat(notifications).hasSize(1);
        // Being told it was rejected without the reason means guessing, re-uploading the same
        // thing, and being rejected again.
        assertThat(notifications.get(0).getBody()).contains("The photo page is cut off.");
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.DOCUMENT_REJECTED);
    }

    @Test
    @DisplayName("Accepting a document says nothing, because it asks nothing of the learner")
    void acceptanceIsNotANotification() {
        Learner learner = learner("ACC");
        LearnerDocument document = documentRepository.save(LearnerDocument.builder()
                .learner(learner).documentType(PoeDocumentType.CV)
                .filePath("lms_secure/cv").originalFilename("cv.pdf")
                .status(ReviewStatus.PENDING).version(1).current(true)
                .uploadedAt(LocalDateTime.now()).uploadedByRole("LEARNER").build());

        documentService.review(document.getId(), ReviewStatus.ACCEPTED, null, "Admin");

        // A badge that lights up for things needing no action stops being read, which then
        // hides the one that mattered.
        assertThat(notificationService.listFor(learner.getId())).isEmpty();
    }

    @Test
    @DisplayName("Publishing material tells everyone enrolled, once each")
    void moduleFanOutTellsEveryoneOnce() {
        Learner a = learner("FAN1");
        Learner b = learner("FAN2");

        int told = notificationService.notifyModuleLearners(List.of(a, b),
                NotificationType.GUIDE_PUBLISHED, "MODULE", 7L, "New material for Core 10022: Week 3 notes");

        assertThat(told).isEqualTo(2);
        assertThat(notificationService.listFor(a.getId())).hasSize(1);
        assertThat(notificationService.listFor(b.getId())).hasSize(1);
        // One body sent to everyone, so it must not name anybody.
        assertThat(notificationService.listFor(a.getId()).get(0).getBody())
                .doesNotContain(b.getFullName());
    }

    @Test
    @DisplayName("A connection that has gone away is dropped the next time anything is sent")
    void deadConnectionsAreReaped() {
        int before = notificationStream.openConnections();

        var emitter = notificationStream.subscribe(1L, NotificationService.LEARNER_ROLE);
        assertThat(notificationStream.openConnections()).isEqualTo(before + 1);

        // Completing the emitter is what a client disappearing looks like from here: the next
        // write fails. The onCompletion callback is invoked by Spring MVC when the request
        // finishes, so it does not fire outside one — which means the send-failure path is the
        // cleanup that actually runs, and therefore the one worth testing.
        emitter.complete();
        notificationStream.publish(1L, NotificationService.LEARNER_ROLE);

        // Phones close connections constantly. An emitter map that only grows would be a slow
        // memory leak on an instance with 512MB.
        assertThat(notificationStream.openConnections()).isEqualTo(before);
    }

    @Test
    @DisplayName("The heartbeat is what discovers a connection nobody is listening to")
    void heartbeatReapsSilently() {
        int before = notificationStream.openConnections();
        var emitter = notificationStream.subscribe(2L, NotificationService.LEARNER_ROLE);
        emitter.complete();

        // Without traffic a proxy or mobile network closes an idle connection quietly, and the
        // server keeps holding an emitter. A write is the only way to find out.
        notificationStream.heartbeat();

        assertThat(notificationStream.openConnections()).isEqualTo(before);
    }

    @Test
    @DisplayName("Pushing to somebody with nothing open is not an error")
    void publishWithNoListenersIsSafe() {
        Learner learner = learner("OFFLINE");

        // The row is the delivery; the stream is only a nudge. A learner whose phone is asleep
        // must still end up with the notification.
        Notification n = notificationService.notifyLearner(learner, NotificationType.FEEDBACK_RELEASED,
                "SESSION", 1L, "Your marking is ready.");

        assertThat(n.getId()).isNotNull();
        assertThat(notificationService.unreadCount(learner.getId())).isEqualTo(1);
    }

    private Learner learner(String tag) {
        long unique = System.nanoTime();
        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("N " + unique).build());
        return learnerRepository.save(Learner.builder()
                .fullName(tag + " Learner").learnerCode(tag + unique)
                .email(tag + unique + "@example.com").phoneNumber("0700000000")
                .learnership(learnership).modules(new HashSet<>()).build());
    }
}
