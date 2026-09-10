package com.example.learnerassignments.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live push to portals that happen to be open.
 *
 * One-way, which is all this needs — nothing is sent back up the wire, so the WebSocket the
 * brief rules out would be machinery for a capability nobody wants.
 *
 * <p><strong>This is an optimisation, not a delivery mechanism.</strong> Notifications live in
 * the database; the stream only tells an open page to go and look. Every client also polls, so
 * a learner on a train, behind a proxy that buffers, or on a phone that slept through the
 * event still sees their badge. If this class stopped working entirely the product would be
 * slower and still correct, which is the property worth protecting.
 */
@Service
@Slf4j
public class NotificationStream {

    /** Longer than the poll interval, so a dropped stream is covered before it is noticed. */
    private static final long TIMEOUT_MS = 15 * 60 * 1000L;

    /**
     * Emitters by recipient. A list per recipient because one person can have the portal open
     * on a phone and a laptop, and both should light up.
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Whether to hold live connections at all.
     *
     * This runs on a 512MB instance and the stream holds one connection per open portal. If
     * that turns out to be the wrong trade on a busy day, turning it off should be an
     * environment variable and a restart, not a code change, a review and a deploy while
     * people are trying to use the thing.
     *
     * Off is not a degraded mode. The badge is delivered by polling either way; the stream
     * only makes it faster.
     */
    @Value("${notifications.stream.enabled:true}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public SseEmitter subscribe(Long userId, String userRole) {
        if (!enabled) {
            return null;
        }
        String key = key(userId, userRole);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));

        try {
            // Something immediately, so proxies that buffer until first byte let the response
            // through and the client knows it is connected rather than merely waiting.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(key, emitter);
        }
        return emitter;
    }

    /** Nudges one recipient's open pages. Never carries the notification itself. */
    public void publish(Long userId, String userRole) {
        send(key(userId, userRole), "notification", "new");
    }

    /**
     * Keeps idle connections alive and reaps dead ones.
     *
     * Without traffic, proxies and mobile networks close an idle connection quietly, and the
     * server keeps an emitter nobody is listening to. A write is the only way to find out.
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        if (!enabled) {
            return;
        }
        emitters.keySet().forEach(key -> send(key, "heartbeat", "."));
    }

    private void send(String key, String eventName, String data) {
        List<SseEmitter> targets = emitters.get(key);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                // A client that has gone away is the normal case, not an error worth logging
                // at every heartbeat: phones close connections constantly.
                remove(key, emitter);
            }
        }
    }

    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> targets = emitters.get(key);
        if (targets != null) {
            targets.remove(emitter);
            if (targets.isEmpty()) {
                emitters.remove(key);
            }
        }
    }

    /** Visible for testing: how many live connections are being held. */
    int openConnections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    private String key(Long userId, String userRole) {
        return userRole + ":" + userId;
    }
}
