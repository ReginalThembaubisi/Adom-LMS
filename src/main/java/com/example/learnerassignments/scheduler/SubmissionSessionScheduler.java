package com.example.learnerassignments.scheduler;

import com.example.learnerassignments.service.SubmissionSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmissionSessionScheduler {

    private final SubmissionSessionService sessionService;

    @Scheduled(cron = "0 * * * * *") // Runs at second 0 of every minute
    public void scheduleSessionStatusUpdates() {
        sessionService.updateSessionStatusesCron();
    }
}
