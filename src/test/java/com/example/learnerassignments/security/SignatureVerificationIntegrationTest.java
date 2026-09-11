package com.example.learnerassignments.security;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one endpoint anyone may call without signing in: {@code GET /api/verify/{code}}.
 *
 * This exercises the real security filter chain (unlike a plain service test), which is the
 * only way to prove {@code /api/verify/**} actually reaches the controller unauthenticated
 * rather than being caught by the {@code .anyRequest().authenticated()} fallback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SignatureVerificationIntegrationTest {

    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;

    @MockBean JavaMailSender mailSender;

    private static final String PW = "Rehearsal#2026";
    private static final String SPECIMEN = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    private Learner learner;

    @BeforeEach
    void seed() {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("Verify SETA").createdAt(LocalDateTime.now()).build());
        learner = learnerRepository.save(Learner.builder()
                .learnerCode("VER" + System.nanoTime()).fullName("Vera Fikile").learnership(learnership)
                .cohort("2026-01").passwordHash(passwordEncoder.encode(PW)).modules(new HashSet<>())
                .createdAt(LocalDateTime.now()).build());
    }

    private String token() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/learners/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learner.getLearnerCode() + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Signing a document then verifying it needs no login, and shows only the safe fields")
    void publicVerificationWorksWithNoAuthentication() throws Exception {
        String token = token();

        MvcResult upload = mockMvc.perform(multipart("/api/me/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf",
                                "%PDF vera's cv".getBytes(StandardCharsets.UTF_8)))
                        .param("document_type", "CV")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        long documentId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        String signBody = objectMapper.writeValueAsString(java.util.Map.of(
                "signableType", "LEARNER_DOCUMENT", "signableId", documentId,
                "specimenImage", SPECIMEN, "password", PW));
        MvcResult sign = mockMvc.perform(post("/api/me/signatures")
                        .contentType(MediaType.APPLICATION_JSON).content(signBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        String code = objectMapper.readTree(sign.getResponse().getContentAsString()).get("verificationCode").asText();

        // No Authorization header at all.
        mockMvc.perform(get("/api/verify/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("CV"))
                .andExpect(jsonPath("$.signerRole").value("LEARNER"))
                .andExpect(jsonPath("$.hashMatches").value(true))
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.valid").value(true))
                // The whole contract: nothing else may appear in this response.
                .andExpect(jsonPath("$.signerId").doesNotExist())
                .andExpect(jsonPath("$.signableId").doesNotExist())
                .andExpect(jsonPath("$.learnerName").doesNotExist())
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(jsonPath("$.specimenImage").doesNotExist())
                .andExpect(jsonPath("$.declarationText").doesNotExist());
    }

    @Test
    @DisplayName("An unknown code is 404, not an error that hints at what went wrong")
    void unknownCodeIsNotFound() throws Exception {
        mockMvc.perform(get("/api/verify/does-not-exist-at-all"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Signing endpoints still require a learner session")
    void signingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/me/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signableType\":\"LEARNER_DOCUMENT\",\"signableId\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
