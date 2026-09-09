package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.LearnerSession;
import com.example.learnerassignments.repository.LearnerSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and resolves opaque learner session tokens.
 *
 * An opaque server-side token was chosen over a JWT so that a session can be revoked the
 * moment a learner logs out or resets their password — a signed JWT stays valid until it
 * expires, which is the wrong default for a portal holding one person's assessment record.
 */
@Service
@RequiredArgsConstructor
public class LearnerTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final LearnerSessionRepository sessionRepository;

    @Value("${learner.session.ttl-hours:24}")
    private long ttlHours;

    /**
     * Mints a new session and returns the raw token. The raw value is returned exactly once,
     * here; only its digest is stored.
     */
    @Transactional
    public IssuedToken issue(Learner learner) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = ENCODER.encodeToString(raw);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(ttlHours);

        sessionRepository.save(LearnerSession.builder()
                .tokenHash(hash(token))
                .learner(learner)
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastSeenAt(now)
                .build());

        return new IssuedToken(token, expiresAt);
    }

    /**
     * Resolves a raw token to its learner, or empty when the token is unknown, revoked or
     * expired. Callers must treat empty as "unauthenticated" — never as "not found".
     *
     * The principal is materialised inside the transaction so the filter never touches a
     * detached lazy proxy.
     */
    @Transactional
    public Optional<LearnerPrincipal> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.findByTokenHash(hash(rawToken))
                .filter(session -> session.isUsableAt(now))
                .map(session -> {
                    session.setLastSeenAt(now);
                    Learner learner = session.getLearner();
                    return new LearnerPrincipal(learner.getId(), learner.getLearnerCode(), learner.getFullName());
                });
    }

    /** Revokes a single session (logout). Silently ignores an unknown token. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.findByTokenHash(hash(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> session.setRevokedAt(LocalDateTime.now()));
    }

    /** Revokes every live session for a learner — used after a password reset. */
    @Transactional
    public void revokeAllFor(Long learnerId) {
        sessionRepository.revokeAllForLearner(learnerId, LocalDateTime.now());
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {}
}
