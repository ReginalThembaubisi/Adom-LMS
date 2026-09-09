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
 * Mints short-lived, single-resource tickets: a signed grant to read one thing, for a few
 * minutes, issued only after the caller has already been authorised for it.
 *
 * A ticket is not an identifier the caller chooses. It is minted server-side, expires, and
 * carries a signature over the resource id, so it cannot be edited into a ticket for
 * somebody else's file — which is exactly what separates it from a learner code.
 *
 * Nothing in the portal uses this yet: submission files are fetched with an Authorization
 * header and rendered from an object URL, which needs no URL-embeddable credential at all.
 * It is here for Phase 8, which emails a link to a finished export — a case where the
 * recipient's browser follows a bare URL and there is no header to send.
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
