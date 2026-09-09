package com.example.learnerassignments.security;

import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.AssessorRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.ModeratorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns the authentication on a request into a {@link StaffPrincipal}.
 *
 * This is the controller-side half, and it is deliberately separate from ScopeService. This
 * one needs a request to exist; ScopeService must not, because Phase 8's exporter calls it
 * from an @Async worker. Merging them would make the scoping decisions unavailable there,
 * and it would pass every test until it didn't.
 */
@Component
@RequiredArgsConstructor
public class CurrentStaff {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final AssessorRepository assessorRepository;
    private final ModeratorRepository moderatorRepository;

    /** The staff identity on this request, or empty when there isn't one. */
    public Optional<StaffPrincipal> resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String username = auth.getName();

        if (hasRole(auth, "ROLE_ADMIN")) {
            return adminRepository.findByUsername(username)
                    .map(a -> StaffPrincipal.admin(a.getId(), username));
        }
        if (hasRole(auth, "ROLE_LECTURER")) {
            return lecturerRepository.findByUsername(username)
                    .map(l -> StaffPrincipal.lecturer(l.getId(), username));
        }
        if (hasRole(auth, "ROLE_ASSESSOR")) {
            return assessorRepository.findByUsername(username)
                    .map(a -> StaffPrincipal.assessor(a.getId(), username));
        }
        if (hasRole(auth, "ROLE_MODERATOR")) {
            return moderatorRepository.findByUsername(username)
                    .map(m -> StaffPrincipal.moderator(m.getId(), username));
        }
        return Optional.empty();
    }

    /** The staff identity, or a 404-shaped failure — never a silent wildcard. */
    public StaffPrincipal require(Authentication auth) {
        return resolve(auth).orElseThrow(() ->
                new com.example.learnerassignments.exception.ResourceNotFoundException(
                        "No staff account for the authenticated user"));
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}
