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
                        .requestMatchers(HttpMethod.GET, "/api/modules/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sessions/active").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/submissions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/download-url").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/graded-download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/submissions/*/graded-download-url").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/learnerships").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/registration-status").permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/register.html",
                                "/login.html",
                                "/landing.html",
                                "/portal.html",
                                "/student-portal.html",
                                "/h2-console/**",
                                "/api/auth/me",
                                "/assets/**",
                                "/uploads/**",
                                "/favicon.svg",
                                "/icons.svg",
                                "/*.css",
                                "/*.js",
                                "/*.ico"
                        ).permitAll()

                        // Admin / Lecturer Protected Endpoints & Dashboards
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin-dashboard.html").hasRole("ADMIN")
                        .requestMatchers("/api/lecturer/**").hasRole("LECTURER")
                        .requestMatchers("/lecturer-dashboard.html").hasRole("LECTURER")

                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, authException) -> {
                    String uri = request.getRequestURI();
                    if (uri.endsWith("/admin-dashboard.html") || uri.equals("/admin")) {
                        response.sendRedirect("/#/admin-login");
                    } else if (uri.endsWith("/lecturer-dashboard.html") || uri.equals("/lecturer")) {
                        response.sendRedirect("/#/lecturer-login");
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
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication required\"}");
                    }
                }));

        return http.build();
    }
}
