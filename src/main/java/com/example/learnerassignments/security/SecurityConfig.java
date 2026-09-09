package com.example.learnerassignments.security;

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
                        // --- Genuinely public: everything needed to get an account and get in ---
                        .requestMatchers(HttpMethod.POST, "/api/learners").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learnerships").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/registration-status").permitAll()

                        // Submission files are fetched by <iframe> and by Google's document
                        // viewer, neither of which can send an Authorization header. The
                        // endpoint is open at this layer and authorises in the controller
                        // against a signed, minutes-long ticket minted for one learner and
                        // one submission — see MeController.issueViewTicket.
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/view").permitAll()

                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/h2-console/**",
                                "/api/auth/me",
                                "/assets/**",
                                "/uploads/**",
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

                        // --- Learner portal ---
                        // Everything a learner reads or writes about themselves. The learner
                        // is resolved from the bearer token, so there is nothing in the path
                        // to tamper with. Previously these lived under /api/learners/{code}
                        // and were open: a learner code is emailed and WhatsApped out, so it
                        // was an identifier, never a credential.
                        .requestMatchers("/api/me/**").hasRole("LEARNER")

                        // --- Admin / Lecturer Protected Endpoints & Dashboards ---
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
