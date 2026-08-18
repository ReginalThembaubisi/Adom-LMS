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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

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
                        // Public Student Endpoints & Pages
                        .requestMatchers(HttpMethod.POST, "/api/learners").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/submissions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/modules").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/timeline").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/facilitators").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/messages").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learners/*/messages/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/learners/*/messages/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/modules/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sessions/active").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/submissions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/view").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/ask").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learnerships").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/registration-status").permitAll()
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

                        // Admin / Lecturer Protected Endpoints & Dashboards
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
