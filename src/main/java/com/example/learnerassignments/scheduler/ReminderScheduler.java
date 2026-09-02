package com.example.learnerassignments.scheduler;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.SubmissionSession;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.SubmissionRepository;
import com.example.learnerassignments.repository.SubmissionSessionRepository;
import com.example.learnerassignments.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final SubmissionSessionRepository sessionRepository;
    private final LearnerRepository learnerRepository;
    private final SubmissionRepository submissionRepository;
    private final EmailService emailService;

    // Track sent reminders to prevent duplicates
    private final Set<String> sentReminders = Collections.synchronizedSet(new HashSet<>());

    @Scheduled(cron = "0 */5 * * * *") // Runs every 5 minutes
    public void sendUpcomingDeadlinesReminders() {
        LocalDateTime now = LocalDateTime.now();
        
        // Use the indexed DB query instead of fetching and filtering all sessions in Java
        List<SubmissionSession> activeSessions = sessionRepository.findActiveSessions(now);

        for (SubmissionSession session : activeSessions) {
            LocalDateTime endTime = session.getEndTime();
            long minutesRemaining = ChronoUnit.MINUTES.between(now, endTime);

            // Check if the session is due in approximately 1 hour (between 50 and 65 minutes)
            if (minutesRemaining >= 50 && minutesRemaining <= 65) {
                Long moduleId = session.getAssignment().getModule().getId();
                List<Learner> enrolledStudents = learnerRepository.findByModules_Id(moduleId);

                for (Learner student : enrolledStudents) {
                    String reminderKey = session.getId() + "_" + student.getId();

                    // Skip if reminder was already sent
                    if (sentReminders.contains(reminderKey)) {
                        continue;
                    }

                    // Skip if the student has already submitted their work for this session
                    boolean alreadySubmitted = submissionRepository.existsByLearnerIdAndSessionId(
                            student.getId(),
                            session.getId()
                    );
                    if (alreadySubmitted) {
                        continue;
                    }

                    // Dispatch reminder email async
                    String formattedDate = endTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    emailService.sendAssignmentReminderEmail(
                            student.getEmail(),
                            student.getFullName(),
                            session.getAssignment().getTitle(),
                            formattedDate
                    );

                    // Mark as sent
                    sentReminders.add(reminderKey);
                    log.info("Queued 1-hour email reminder for student {} ({}) on assignment {}", 
                            student.getFullName(), student.getEmail(), session.getAssignment().getTitle());
                }
            }
        }
    }
}
