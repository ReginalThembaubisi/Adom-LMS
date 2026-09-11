package com.example.learnerassignments.service;

import com.example.learnerassignments.model.AuditLog;
import com.example.learnerassignments.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Runs in its own transaction so a logging failure never blocks or rolls back
    // the destructive action it is recording.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Authentication auth, String action, String entityType, Long entityId, String details) {
        String role = auth != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority()
                : "UNKNOWN";
        String username = auth != null ? auth.getName() : "UNKNOWN";
        log(role, username, action, entityType, entityId, details);
    }

    /**
     * As {@link #log(Authentication, String, String, Long, String)}, for a caller with no
     * request-bound {@code Authentication} to read — the PoE export worker (Phase 8) runs on
     * an {@code @Async} thread with no request on it, the same reason {@code ScopeService}
     * takes an identity as a parameter rather than reading the security context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorRole, String actorUsername, String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorRole(actorRole == null ? "UNKNOWN" : actorRole)
                    .actorUsername(actorUsername == null ? "UNKNOWN" : actorUsername)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action={} entityType={} entityId={}", action, entityType, entityId, e);
        }
    }
}
