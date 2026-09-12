package com.example.learnerassignments.service;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Notification;
import com.example.learnerassignments.model.NotificationType;
import com.example.learnerassignments.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Writing and reading the things a learner should be told.
 *
 * One place, so the rules that apply to all of them are applied once: a notification never
 * contains a link, never names another learner, and is only ever addressed to somebody by the
 * identity the request was authenticated as.
 *
 * <p><strong>{@link #notifyLearner} runs in its own transaction, deliberately.</strong> Every
 * caller of this method is doing something more important than sending a notification — marking
 * a document reviewed, releasing a session's feedback — and that action must still succeed even
 * if telling the learner about it fails. On Postgres specifically, a failing statement poisons
 * the rest of whatever transaction it ran in: a caller catching the Java exception is not enough
 * to save its own, earlier writes in the same transaction, because the connection itself refuses
 * any further commands until it is rolled back. {@code REQUIRES_NEW} gives this its own
 * connection, so a failure here can only roll back the notification, never the caller's own
 * work. Found in production: rejecting a learner's document (which writes the decision, then
 * notifies) came back "the database rejected that change... nothing was saved" — the reviewer's
 * decision itself was being rolled back by a notification write it had no reason to depend on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    public static final String LEARNER_ROLE = "LEARNER";

    private final NotificationRepository notificationRepository;
    private final NotificationStream stream;

    /**
     * A self-reference to this bean's Spring proxy, {@code @Lazy} to break the construction
     * cycle. {@link #notifyModuleLearners} calls {@link #notifyLearner} in a loop; routing that
     * call through {@code self} rather than a plain {@code this.notifyLearner(...)} is what
     * makes {@code REQUIRES_NEW} actually apply per learner — a same-class self-invocation
     * bypasses the proxy that either annotation depends on, the same trap this codebase has hit
     * with {@code @Async}/{@code @Transactional} before (see {@code PoeExportService} and
     * {@code SignatureService}). Without it, one bad row here would still take the whole fan-out
     * down with it.
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private NotificationService self;

    /**
     * Tells one learner one thing.
     *
     * The body is written by the caller because only the caller knows what happened, but it
     * goes through here so the no-links rule has somewhere to live. Learners were told in the
     * announcement that this system will never send them a link; a notification carrying one
     * is indistinguishable from a phishing message and teaches them to click.
     *
     * <p>Runs in its own transaction — see the class doc. A caller for whom this failing must
     * not touch its own, more important writes should call this expecting it to throw on
     * failure and catch that at the call site, not assume a swallowed failure here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification notifyLearner(Learner learner, NotificationType type,
                                      String refType, Long refId, String body) {
        if (learner == null) {
            return null;
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(learner.getId())
                .userRole(LEARNER_ROLE)
                .type(type)
                .refType(refType)
                .refId(refId)
                .body(stripLinks(body))
                .createdAt(LocalDateTime.now())
                .build());

        // Push after the row exists, so a learner who reacts to the nudge finds it there.
        stream.publish(learner.getId(), LEARNER_ROLE);
        return saved;
    }

    /**
     * Tells everyone enrolled on a module that new material is up.
     *
     * The only fan-out in the system, so it is the only place a mistake reaches a whole cohort
     * at once. Two consequences worth stating: it reads enrolment from the join table, which is
     * why that table being populated mattered; and it says what was published without naming
     * the learner, so one body is safe to send to all of them.
     *
     * <p>One learner's notification failing (through {@code self}, its own transaction) is
     * caught and logged here rather than allowed to stop the rest of the cohort being told —
     * the same "one bad row does not sink the batch" discipline {@code PoeExportService}
     * applies to a bad file mid-export.
     *
     * Returns how many people were told, because "published a guide" and "told forty people"
     * are different things and the caller should be able to log the second.
     */
    @Transactional
    public int notifyModuleLearners(List<Learner> learners, NotificationType type,
                                    String refType, Long refId, String body) {
        int told = 0;
        for (Learner learner : learners) {
            try {
                if (self.notifyLearner(learner, type, refType, refId, body) != null) {
                    told++;
                }
            } catch (Exception e) {
                log.error("Could not notify learner {} ({}): {}",
                        learner == null ? null : learner.getId(), refType, e.getMessage(), e);
            }
        }
        if (told > 0) {
            log.info("Notified {} learner(s): {}", told, body);
        }
        return told;
    }

    @Transactional(readOnly = true)
    public List<Notification> listFor(Long learnerId) {
        return notificationRepository.findByUserIdAndUserRoleOrderByCreatedAtDesc(learnerId, LEARNER_ROLE);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long learnerId) {
        return notificationRepository.countByUserIdAndUserRoleAndReadAtIsNull(learnerId, LEARNER_ROLE);
    }

    /**
     * Marks everything this learner has as read.
     *
     * Scoped by the learner resolved from their session, never by an id the caller supplies —
     * otherwise clearing somebody else's badge is a request away.
     */
    @Transactional
    public int markAllRead(Long learnerId) {
        List<Notification> unread = notificationRepository
                .findByUserIdAndUserRoleOrderByCreatedAtDesc(learnerId, LEARNER_ROLE).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    /**
     * Removes anything that looks like a URL.
     *
     * A belt to the braces of writing the bodies carefully: the rule matters more than any one
     * call site, and the next person adding a notification will not read this class first.
     */
    private String stripLinks(String body) {
        if (body == null) {
            return null;
        }
        String cleaned = body.replaceAll("(?i)\\b(?:https?://|www\\.)\\S+", "").replaceAll("\\s{2,}", " ").trim();
        if (!cleaned.equals(body.trim())) {
            log.warn("A notification body contained a link and it was removed. Learners are told "
                    + "this system never sends links; keep them out of the message instead.");
        }
        return cleaned;
    }
}
