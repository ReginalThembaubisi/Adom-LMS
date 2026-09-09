package com.example.learnerassignments.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated learner, resolved from a bearer token. This is the only place a
 * learner identity may come from — no controller derives it from a path variable, query
 * parameter or request body.
 */
public record LearnerPrincipal(Long learnerId, String learnerCode, String fullName) {

    public static final String ROLE = "ROLE_LEARNER";

    public static Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
    }

    /** Used as {@code Authentication#getName()}. */
    @Override
    public String toString() {
        return learnerCode;
    }
}
