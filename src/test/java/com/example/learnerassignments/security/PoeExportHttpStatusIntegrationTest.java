package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.model.Assessor;
import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.AssessorRepository;
import com.example.learnerassignments.repository.LearnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The status code an HTTP caller actually receives, not just the exception type a service
 * throws internally.
 *
 * The gap between those two things is exactly how a 403 came back as a 500 the first time this
 * ran against a real container: {@code AccessDeniedException} is Spring Security's idiomatic
 * "not authorised" signal, and the textbook answer is that the security filter chain converts
 * it to 403 — which is true only when nothing upstream of the filter chain already caught it.
 * {@code GlobalExceptionHandler}'s catch-all does exactly that, inside Spring MVC's own
 * exception resolution, before the exception ever gets a chance to reach the filter chain. A
 * unit test asserting {@code isInstanceOf(AccessDeniedException.class)} cannot see that gap;
 * only a request that actually goes through the whole stack can.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PoeExportHttpStatusIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AssessorRepository assessorRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired LearnershipRepository learnershipRepository;

    private static final String PW = "staff-password";
    private String assessorUser;

    @BeforeEach
    void seed() {
        Learnership learnership = learnershipRepository.save(
                Learnership.builder().name("HTTP Status " + System.nanoTime()).build());
        assessorUser = "assessor-" + System.nanoTime();
        assessorRepository.save(Assessor.builder().fullName("An Assessor").username(assessorUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
        adminRepository.save(Admin.builder().username("admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
        this.learnershipId = learnership.getId();
    }

    private Long learnershipId;

    @Test
    @DisplayName("An assessor requesting a learnership-wide export gets 403, not 500")
    void nonAdminScopeIsForbiddenNotServerError() throws Exception {
        mockMvc.perform(post("/api/poe/export")
                        .header("Authorization", basic(assessorUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"LEARNERSHIP\",\"learnershipId\":" + learnershipId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An unauthenticated request is rejected before it reaches the export logic at all")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/poe/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"LEARNERSHIP\",\"learnershipId\":" + learnershipId + "}"))
                .andExpect(status().isUnauthorized());
    }

    private String basic(String user) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + PW).getBytes(StandardCharsets.UTF_8));
    }
}
