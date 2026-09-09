package com.example.learnerassignments.security;

/**
 * Who is asking, as a plain value.
 *
 * Deliberately not an Authentication and not a lookup into SecurityContextHolder: ScopeService
 * is called from controllers today and from the Phase 8 exporter later, which runs on an
 * @Async worker with no request bound to the thread. Anything that reaches into the security
 * context would work in every test and fail there.
 *
 * CurrentLearner does read the context, and stays a thin controller-side helper for that
 * reason. The two are not the same thing and should not be merged: one answers "who is on
 * this request", the other answers "what may this identity reach", and only the first needs
 * a request to exist.
 */
public record StaffPrincipal(StaffRole role, Long id, String username) {

    public enum StaffRole { ADMIN, LECTURER, ASSESSOR, MODERATOR }

    public boolean isAdmin() {
        return role == StaffRole.ADMIN;
    }

    public static StaffPrincipal admin(Long id, String username) {
        return new StaffPrincipal(StaffRole.ADMIN, id, username);
    }

    public static StaffPrincipal lecturer(Long id, String username) {
        return new StaffPrincipal(StaffRole.LECTURER, id, username);
    }

    public static StaffPrincipal assessor(Long id, String username) {
        return new StaffPrincipal(StaffRole.ASSESSOR, id, username);
    }

    public static StaffPrincipal moderator(Long id, String username) {
        return new StaffPrincipal(StaffRole.MODERATOR, id, username);
    }
}
