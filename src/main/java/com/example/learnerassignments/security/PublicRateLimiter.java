package com.example.learnerassignments.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small in-memory sliding-window limit for the endpoints anyone on the internet can call
 * without signing in: submitting an application and looking up its status.
 *
 * In memory on purpose — this runs as a single instance, and the goal is to blunt a script
 * hammering the form or guessing reference numbers, not to meter legitimate use exactly. A
 * restart forgets the counts, which is harmless.
 */
@Component
public class PublicRateLimiter {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Counts one hit for {@code bucket} from this caller, or answers 429 when they have used
     * up {@code max} hits within {@code window}.
     */
    public void check(HttpServletRequest request, String bucket, int max, Duration window) {
        String key = bucket + "|" + clientIp(request);
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();

        if (hits.size() > MAX_TRACKED_KEYS) {
            // Bounded memory under a flood of distinct addresses: drop everything stale.
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long last = e.getValue().peekLast();
                    return last == null || last < cutoff;
                }
            });
        }

        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many attempts. Please wait a few minutes and try again.");
            }
            times.addLast(now);
        }
    }

    /**
     * The caller's address. Render's proxy appends the address it saw to X-Forwarded-For, so
     * the last entry is the one a client cannot forge; anything before it is client-supplied.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /** Test hook: forget every count. */
    public void reset() {
        hits.clear();
    }
}
