package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.model.Learner;
import com.example.learnerassignments.model.Learnership;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.LearnerDocumentRepository;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LearnershipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The legacy folder importer through the real HTTP stack: role gating, and one full
 * preview -> confirm round trip proving the controller wiring — not just the service — does
 * what the brief requires.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LegacyImportHttpIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminRepository adminRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;

    private static final String PW = "admin-password";
    private String adminUser;

    @BeforeEach
    void seed() {
        adminUser = "admin-" + System.nanoTime();
        adminRepository.save(Admin.builder().username(adminUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
    }

    private MockMultipartFile zip(String... pathAndContent) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < pathAndContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(pathAndContent[i]));
                zos.write(pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "legacy.zip", "application/zip", baos.toByteArray());
    }

    @Test
    @DisplayName("The import endpoint requires an admin session")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/admin/poe/import").file(zip("a.txt", "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A learner cannot reach the import endpoint")
    void learnerCannotImport() throws Exception {
        Learnership ls = learnershipRepository.save(Learnership.builder().name("HTTP Import").build());
        Learner learner = learnerRepository.save(Learner.builder()
                .learnerCode("HI" + System.nanoTime()).fullName("Not Admin").learnership(ls)
                .cohort("2026").passwordHash(passwordEncoder.encode(PW)).modules(new HashSet<>())
                .createdAt(LocalDateTime.now()).build());

        mockMvc.perform(multipart("/api/admin/poe/import").file(zip("a.txt", "x"))
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(learner.getLearnerCode(), PW)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Preview then confirm through the real controller writes a real LearnerDocument")
    void previewThenConfirmRoundTrip() throws Exception {
        Learnership ls = learnershipRepository.save(Learnership.builder().name("HTTP Import Real").build());
        Learner learner = learnerRepository.save(Learner.builder()
                .learnerCode("HI" + System.nanoTime()).fullName("Http Import Learner").learnership(ls)
                .cohort("2026").modules(new HashSet<>()).createdAt(LocalDateTime.now()).build());

        var previewResult = mockMvc.perform(multipart("/api/admin/poe/import")
                        .file(zip("Http Import Learner/1. PERSONAL DETAILS/CV.pdf", "%PDF cv"))
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(adminUser, PW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andExpect(jsonPath("$.importableCount").value(1))
                .andReturn();
        long batchId = objectMapper.readTree(previewResult.getResponse().getContentAsString()).get("id").asLong();

        assertThat(documentRepository.findByLearner_IdOrderByUploadedAtDesc(learner.getId())).isEmpty();

        mockMvc.perform(post("/api/admin/poe/import/" + batchId + "/confirm")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(adminUser, PW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.importedCount").value(1));

        assertThat(documentRepository.findByLearner_IdOrderByUploadedAtDesc(learner.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Preview then cancel through the real controller writes nothing")
    void previewThenCancelRoundTrip() throws Exception {
        Learnership ls = learnershipRepository.save(Learnership.builder().name("HTTP Import Cancel").build());
        Learner learner = learnerRepository.save(Learner.builder()
                .learnerCode("HI" + System.nanoTime()).fullName("Cancel Http Learner").learnership(ls)
                .cohort("2026").modules(new HashSet<>()).createdAt(LocalDateTime.now()).build());

        var previewResult = mockMvc.perform(multipart("/api/admin/poe/import")
                        .file(zip("Cancel Http Learner/1. PERSONAL DETAILS/CV.pdf", "%PDF cv"))
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(adminUser, PW)))
                .andExpect(status().isCreated())
                .andReturn();
        long batchId = objectMapper.readTree(previewResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/poe/import/" + batchId + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(adminUser, PW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(documentRepository.findByLearner_IdOrderByUploadedAtDesc(learner.getId())).isEmpty();
    }
}
