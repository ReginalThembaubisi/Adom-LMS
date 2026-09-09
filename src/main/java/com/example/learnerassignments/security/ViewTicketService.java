package com.example.learnerassignments.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Mints short-lived, single-resource tickets so a browser context that cannot send an
 * Authorization header — an {@code <iframe>} src, or Google's Docs viewer fetching a Word
 * file — can still read one file the caller has already been authorised for.
 *
 * A ticket is not an identifier the caller chooses: it is minted server-side for one
 * submission after the ownership check has passed, expires in minutes, and carries a
 * signature, so it cannot be edited into a ticket for someone else's file. That is what
 * separates it from the learner code it replaces, which was permanent, guessable and
 * accepted for any record.
 */
@Service
public class ViewTicketService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${learner.view-ticket.secret:}")
    private String configuredSecret;

    @Value("${learner.view-ticket.ttl-seconds:300}")
    private long ttlSeconds;

    private byte[] key;

    @PostConstruct
    void initialiseKey() {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            key = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            // No secret configured: generate one per boot. Tickets then die with a restart,
            // which is acceptable given their few-minute lifetime.
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        }
    }

    /** Issues a ticket for one learner to read one submission. */
    public Ticket issue(Long learnerId, Long submissionId) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = learnerId + ":" + submissionId + ":" + expiresAt;
        return new Ticket(payload + ":" + sign(payload), expiresAt);
    }

    /**
     * Returns the learner the ticket was minted for, but only when the ticket is well-formed,
     * unexpired, correctly signed, and issued for this exact submission.
     */
    public Optional<Long> verify(String ticket, Long submissionId) {
        if (ticket == null || ticket.isBlank() || submissionId == null) {
            return Optional.empty();
        }
        String[] parts = ticket.split(":");
        if (parts.length != 4) {
            return Optional.empty();
        }
        String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
        if (!constantTimeEquals(sign(payload), parts[3])) {
            return Optional.empty();
        }
        try {
            if (!submissionId.equals(Long.parseLong(parts[1]))) {
                return Optional.empty();
            }
            if (Long.parseLong(parts[2]) <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(parts[0]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign view ticket", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        try {
            return MessageDigest.isEqual(DECODER.decode(expected), DECODER.decode(actual));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record Ticket(String value, long expiresAtEpochSecond) {}
}
