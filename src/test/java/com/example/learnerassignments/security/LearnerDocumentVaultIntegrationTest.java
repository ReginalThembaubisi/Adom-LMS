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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The learner document vault.
 *
 * Learners supply their own CV, ID copy, matric certificate and learnership agreement. Nothing
 * is overwritten: re-uploading supersedes and keeps the previous version, so a document that
 * was rejected and resupplied still shows what was sent the first time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnerDocumentVaultIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnershipRepository learnershipRepository;
    @Autowired LearnerDocumentRepository documentRepository;
    @Autowired AdminRepository adminRepository;

    @MockBean JavaMailSender mailSender;

    private static final String PW = "Rehearsal#2026";

    private Learner learnerA;
    private Learner learnerB;
    private String adminUser;

    @BeforeEach
    void seed() {
        Learnership learnership = learnershipRepository.save(Learnership.builder()
                .name("MICT SETA").createdAt(LocalDateTime.now()).build());
        learnerA = learner("202600001", "Amanda Mndawe", learnership);
        learnerB = learner("202600002", "Bongani Sithole", learnership);

        adminUser = "admin-" + System.nanoTime();
        adminRepository.save(Admin.builder().username(adminUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
    }

    private Learner learner(String code, String name, Learnership ls) {
        return learnerRepository.save(Learner.builder()
                .learnerCode(code).fullName(name).learnership(ls).cohort("2026-01")
                .passwordHash(passwordEncoder.encode(PW)).modules(new HashSet<>())
                .createdAt(LocalDateTime.now()).build());
    }

    private String tokenFor(String learnerCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/learners/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + learnerCode + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String adminAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((adminUser + ":" + PW).getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pdf(String filename, String body) {
        return new MockMultipartFile("file", filename, "application/pdf", body.getBytes(StandardCharsets.UTF_8));
    }

    private Long upload(String token, String type, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/me/documents")
                        .file(file).param("document_type", type)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // --- Versioning ---

    @Test
    @DisplayName("uploading a CV twice keeps both, with one current")
    void reuploadSupersedesRatherThanOverwrites() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());

        upload(token, "CV", pdf("cv.pdf", "%PDF first attempt"));
        upload(token, "CV", pdf("cv-updated.pdf", "%PDF second attempt"));

        var all = documentRepository.findByLearner_IdOrderByUploadedAtDesc(learnerA.getId());
        assertThat(all).as("nothing is overwritten").hasSize(2);
        assertThat(all).filteredOn(d -> Boolean.TRUE.equals(d.getCurrent()))
                .as("exactly one version counts")
                .hasSize(1)
                .allSatisfy(d -> {
                    assertThat(d.getVersion()).isEqualTo(2);
                    assertThat(d.getOriginalFilename()).isEqualTo("cv-updated.pdf");
                });
    }

    @Test
    @DisplayName("the portal shows a slot for every required document, supplied or not")
    void slotsExistBeforeAnythingIsUploaded() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());

        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(4))
                .andExpect(jsonPath("$.requiredTotal").value(4))
                .andExpect(jsonPath("$.requiredAccepted").value(0))
                .andExpect(jsonPath("$.slots[0].current").doesNotExist());
    }

    // --- Ownership ---

    @Test
    @DisplayName("learner A cannot read learner B's document")
    void crossLearnerAccessIsNotFound() throws Exception {
        String tokenB = tokenFor(learnerB.getLearnerCode());
        Long bDocument = upload(tokenB, "ID_COPY", pdf("id.pdf", "%PDF bongani id"));

        String tokenA = tokenFor(learnerA.getLearnerCode());
        mockMvc.perform(get("/api/me/documents/" + bDocument + "/view")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a learner's own document list contains only their own")
    void listIsScopedToTheOwner() throws Exception {
        upload(tokenFor(learnerB.getLearnerCode()), "CV", pdf("b-cv.pdf", "%PDF b"));

        String tokenA = tokenFor(learnerA.getLearnerCode());
        upload(tokenA, "CV", pdf("a-cv.pdf", "%PDF a"));

        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].current.originalFilename").value("a-cv.pdf"));
    }

    @Test
    @DisplayName("documents need a session at all")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/me/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/me/documents")
                        .file(pdf("cv.pdf", "%PDF")).param("document_type", "CV"))
                .andExpect(status().isUnauthorized());
    }

    // --- Review ---

    @Test
    @DisplayName("a rejected document shows the reviewer's note to the learner")
    void rejectionNoteReachesTheLearner() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        Long documentId = upload(token, "ID_COPY", pdf("id.pdf", "%PDF blurry"));

        mockMvc.perform(put("/api/admin/documents/" + documentId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"note\":\"The ID number is not readable — please rescan.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.documentType=='ID_COPY')].current.status").value("REJECTED"))
                .andExpect(jsonPath("$.slots[?(@.documentType=='ID_COPY')].current.reviewNote")
                        .value("The ID number is not readable — please rescan."));
    }

    @Test
    @DisplayName("a rejection without a reason is refused")
    void rejectionNeedsAReason() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        Long documentId = upload(token, "CV", pdf("cv.pdf", "%PDF"));

        // Rejecting silently sends the learner back to a screen that tells them to try again
        // and nothing else.
        mockMvc.perform(put("/api/admin/documents/" + documentId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("accepting counts towards the learner's completeness")
    void acceptanceCounts() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        Long documentId = upload(token, "MATRIC", pdf("matric.pdf", "%PDF"));

        mockMvc.perform(put("/api/admin/documents/" + documentId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"note\":\"Clear copy, thank you.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/documents").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.requiredAccepted").value(1));
    }

    @Test
    @DisplayName("a learner cannot review their own document")
    void learnersCannotReviewThemselves() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        Long documentId = upload(token, "CV", pdf("cv.pdf", "%PDF"));

        mockMvc.perform(put("/api/admin/documents/" + documentId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden());
    }

    // --- Validation ---

    @Test
    @DisplayName("only the accepted formats are taken")
    void extensionAllowlist() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());

        mockMvc.perform(multipart("/api/me/documents")
                        .file(new MockMultipartFile("file", "cv.exe", "application/octet-stream", "MZ".getBytes()))
                        .param("document_type", "CV")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        for (String name : new String[]{"cv.pdf", "id.jpg", "id.jpeg", "matric.png", "agreement.docx"}) {
            mockMvc.perform(multipart("/api/me/documents")
                            .file(new MockMultipartFile("file", name, "application/octet-stream", "data".getBytes()))
                            .param("document_type", "OTHER")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("a file over ten megabytes is refused")
    void sizeCap() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        byte[] tooBig = new byte[(10 * 1024 * 1024) + 1];

        mockMvc.perform(multipart("/api/me/documents")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig))
                        .param("document_type", "CV")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown document type is refused rather than filed as Other")
    void unknownTypeIsRefused() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());

        mockMvc.perform(multipart("/api/me/documents")
                        .file(pdf("x.pdf", "%PDF")).param("document_type", "PASSPORT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a malformed upload is a client error, not an internal one")
    void clientMistakesAreNotFiveHundreds() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());

        // Omitting document_type used to answer 500 with the internal binding message. The
        // caller could not tell they had sent a bad request, and an error-rate graph could not
        // tell a real fault from someone's typo.
        mockMvc.perform(multipart("/api/me/documents")
                        .file(pdf("cv.pdf", "%PDF"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/me/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    // --- Admin view ---

    @Test
    @DisplayName("an admin sees every version a learner has supplied")
    void adminSeesTheHistory() throws Exception {
        String token = tokenFor(learnerA.getLearnerCode());
        upload(token, "CV", pdf("cv.pdf", "%PDF first"));
        upload(token, "CV", pdf("cv2.pdf", "%PDF second"));

        mockMvc.perform(get("/api/admin/learners/" + learnerA.getId() + "/documents")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
