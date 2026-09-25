package com.example.learnerassignments.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LearnerTokenAuthenticationFilter learnerTokenAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        // The container re-runs this filter chain for the ERROR dispatch, and
                        // that second pass carries no authentication. Without this line a
                        // genuine 403 from AccessDeniedHandlerImpl — which reports itself with
                        // sendError — is re-secured as an anonymous request for /error, denied
                        // again, and rewritten by the entry point below into a 401. Spring Boot
                        // permits these dispatch types in its default chain; defining our own
                        // gave that up silently. MockMvc does not perform error dispatch, so
                        // only a real container shows it.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()

                        // --- Genuinely public: everything needed to get an account and get in ---
                        .requestMatchers(HttpMethod.POST, "/api/learners").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learnerships").permitAll()

                        // The public website: open learnership adverts, the application form
                        // and the applicant's own status check. Rate limited per caller in
                        // PublicApplicationController; the status check needs both the
                        // reference and the ID number, so neither alone reveals anything.
                        .requestMatchers(HttpMethod.GET, "/api/learnerships/openings", "/api/learnerships/openings/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/applications", "/api/applications/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/registration-status").permitAll()

                        // The Phase 9 public signature-verification page. Deliberately open to
                        // anyone with a code — that is the whole point of a verification page —
                        // and SignatureService.verify() is the sole gate on what it returns:
                        // document type, signer role, date, hash match, nothing else.
                        .requestMatchers(HttpMethod.GET, "/api/verify/**").permitAll()

                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/h2-console/**",
                                "/api/auth/me",
                                "/assets/**",
                                "/favicon.svg",
                                "/icons.svg",
                                "/*.css",
                                "/*.js",
                                "/*.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/img/**",
                                "/logo/**",
                                "/javascript/**",
                                "/dist/**",
                                "/Online Exam/**",
                                "/Online%20Exam/**",
                                "/Admin/**",
                                "/cs/**",
                                "/File/**"
                        ).permitAll()

                        // Facilitator guides and assignment briefs, when stored on local disk
                        // rather than Cloudinary. This used to be open, which also made every
                        // learner submission written here readable by filename — the names are
                        // built from the learner code and session id. Submissions have moved
                        // out of this directory (see LegacySubmissionFileMigration); requiring
                        // authentication here means a future change that puts private files
                        // back cannot silently re-expose them.
                        //
                        // Still unscoped, though: any signed-in learner can read any guide by
                        // path, including modules they are not enrolled on. Low severity while
                        // this only serves course material rather than personal information —
                        // Phase 4 replaces static serving with streamed, ownership-checked
                        // delivery and resolves it. Do not put anything personal here first.
                        .requestMatchers("/uploads/**").authenticated()

                        // Submission files. Authenticated at the filter, then authorised per
                        // record in the controller — the learner who owns it, or staff. It was
                        // briefly open here, because the staff dashboards passed base64 Basic
                        // credentials as ?authToken and the filter cannot see a credential in a
                        // query string. Every caller now sends a header, so the controller check
                        // is the second layer rather than the only one.
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/view").authenticated()

                        // --- Learner portal ---
                        // Everything a learner reads or writes about themselves. The learner
                        // is resolved from the bearer token, so there is nothing in the path
                        // to tamper with. Previously these lived under /api/learners/{code}
                        // and were open: a learner code is emailed and WhatsApped out, so it
                        // was an identifier, never a credential.
                        .requestMatchers("/api/me/**").hasRole("LEARNER")

                        // --- Admin / Lecturer Protected Endpoints & Dashboards ---
                        // Learners read modules and sessions through /api/me, which scopes to
                        // their enrolment. These unscoped views are staff-only, so a learner
                        // cannot read around that scope via the staff route.
                        //
                        // Creating, opening and closing a session is the coursework owner's
                        // job. Assessors and moderators were admitted here alongside the reads
                        // when there was nothing to scope them by, which let an assessor open
                        // and close submission windows for the whole cohort.
                        .requestMatchers(HttpMethod.POST, "/api/sessions/**").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers(HttpMethod.PUT, "/api/sessions/**").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers(HttpMethod.DELETE, "/api/sessions/**").hasAnyRole("ADMIN", "LECTURER")

                        // Reads stay open to all four roles at this layer and are narrowed per
                        // record by ScopeService in the controllers, which is where the
                        // assignment rows can actually be consulted. A role check alone cannot
                        // express "only the modules your assigned learners are enrolled on".
                        .requestMatchers("/api/modules/**").hasAnyRole("ADMIN", "LECTURER", "ASSESSOR", "MODERATOR")
                        .requestMatchers("/api/sessions/**").hasAnyRole("ADMIN", "LECTURER", "ASSESSOR", "MODERATOR")

                        // The PoE export (Phase 8). Role-gated the same way as modules/sessions
                        // above: open to the three roles who can request one, narrowed per
                        // scope type by PoeExportService — a cohort or whole-learnership export
                        // is admin-only there, a moderation sample resolves to the caller's own
                        // ScopeService reach. Not LECTURER: nothing gives a facilitator a reason
                        // to pull a portfolio bundle.
                        .requestMatchers("/api/poe/**").hasAnyRole("ADMIN", "ASSESSOR", "MODERATOR")
                        // The full roster, including every learner code in the cohort.
                        .requestMatchers(HttpMethod.GET, "/api/learners").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers(HttpMethod.POST, "/api/assignments/**").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers(HttpMethod.PUT, "/api/assignments/**").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers(HttpMethod.DELETE, "/api/assignments/**").hasAnyRole("ADMIN", "LECTURER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin-dashboard.html").hasRole("ADMIN")
                        .requestMatchers("/api/lecturer/**").hasRole("LECTURER")
                        .requestMatchers("/lecturer-dashboard.html").hasRole("LECTURER")
                        .requestMatchers("/api/moderator/**").hasRole("MODERATOR")
                        .requestMatchers("/moderator-dashboard.html").hasRole("MODERATOR")
                        .requestMatchers("/api/assessor/**").hasRole("ASSESSOR")
                        .requestMatchers("/assessor-dashboard.html").hasRole("ASSESSOR")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(learnerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    if (uri.endsWith("/admin-dashboard.html") || uri.equals("/admin")) {
                        response.sendRedirect("/#/admin-login");
                    } else if (uri.endsWith("/lecturer-dashboard.html") || uri.equals("/lecturer")) {
                        response.sendRedirect("/#/lecturer-login");
                    } else if (uri.endsWith("/moderator-dashboard.html") || uri.equals("/moderator")) {
                        response.sendRedirect("/#/moderator-login");
                    } else if (uri.endsWith("/assessor-dashboard.html") || uri.equals("/assessor")) {
                        response.sendRedirect("/#/assessor-login");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication required\"}");
                    }
                }))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    if (uri.endsWith("/admin-dashboard.html") || uri.equals("/admin")) {
                        response.sendRedirect("/#/admin-login");
                    } else if (uri.endsWith("/lecturer-dashboard.html") || uri.equals("/lecturer")) {
                        response.sendRedirect("/#/lecturer-login");
                    } else if (uri.endsWith("/moderator-dashboard.html") || uri.equals("/moderator")) {
                        response.sendRedirect("/#/moderator-login");
                    } else if (uri.endsWith("/assessor-dashboard.html") || uri.equals("/assessor")) {
                        response.sendRedirect("/#/assessor-login");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication required\"}");
                    }
                }));

        return http.build();
    }
}
