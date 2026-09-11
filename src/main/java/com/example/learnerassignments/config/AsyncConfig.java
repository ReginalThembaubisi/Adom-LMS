package com.example.learnerassignments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    /**
     * The PoE export worker (Phase 8).
     *
     * Core and max pool size are both 1, deliberately, on a 512MB instance: building a
     * whole-learnership zip holds every fetched file's bytes plus the zip's own write buffer
     * at once, and two of those running together is the more realistic way this box runs out
     * of memory than any one export alone. A queue of 20 lets requests queue up rather than be
     * rejected outright — an export is not time-critical the way a page load is, so waiting
     * behind another export is a wait, not a failure.
     */
    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("poe-export-");
        executor.initialize();
        return executor;
    }

    /**
     * The Phase 9 signature-certificate stamper.
     *
     * A small pool, not one-at-a-time like the export worker: stamping is a single PDF page
     * plus a QR code, not a whole learnership's files held in memory at once, so several can
     * run together on this instance without the same pressure. A queue still exists so a burst
     * of signings waits rather than getting rejected.
     */
    @Bean(name = "signatureTaskExecutor")
    public Executor signatureTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("signature-stamp-");
        executor.initialize();
        return executor;
    }
}
