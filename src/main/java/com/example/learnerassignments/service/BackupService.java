package com.example.learnerassignments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Independent, app-level backup of the database, on top of whatever backup plan the
// hosting provider (Render) offers. Dumps every core table straight via JDBC (not JPA
// entities, to avoid lazy-loading/serialization issues) into one JSON file, then uploads
// it to Cloudinary so restoring data doesn't depend on Render's backup retention/plan.
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final JdbcTemplate jdbcTemplate;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    private static final List<String> BACKUP_TABLES = List.of(
            "admins", "learnerships", "lecturers", "moderators", "assessors",
            "learners", "categories", "modules", "module_files", "assignments",
            "submission_sessions", "submissions", "learner_modules", "learner_code_sequences"
    );

    @Scheduled(cron = "0 0 3 * * *") // Daily at 03:00 server time
    public void runScheduledBackup() {
        if (!cloudinaryService.isConfigured()) {
            log.warn("Skipping scheduled backup: Cloudinary is not configured.");
            return;
        }
        try {
            String filename = performBackup();
            log.info("Scheduled backup completed: {}", filename);
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }

    public String performBackup() throws Exception {
        Map<String, Object> dump = new LinkedHashMap<>();
        dump.put("backupTimestamp", LocalDateTime.now().toString());

        for (String table : BACKUP_TABLES) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
                dump.put(table, rows);
            } catch (Exception e) {
                log.warn("Skipping table '{}' in backup: {}", table, e.getMessage());
            }
        }

        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dump);
        String filename = "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        cloudinaryService.uploadBackup(json, filename);
        return filename;
    }
}
