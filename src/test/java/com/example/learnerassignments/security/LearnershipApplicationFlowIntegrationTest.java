package com.example.learnerassignments.security;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The website-to-LMS admissions flow: an admin publishes a learnership advert, the public
 * applies through the website, staff move the application through selection, and an
 * accepted applicant is enrolled as a learner with their documents carried across.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnershipApplicationFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminRepository adminRepository;
    @Autowired LearnerRepository learnerRepository;
    @Autowired LearnerDocumentRepository learnerDocumentRepository;
    @Autowired LearnershipApplicationRepository applicationRepository;
    @Autowired PublicRateLimiter rateLimiter;

    @MockBean JavaMailSender mailSender;

    private static final String PW = "Rehearsal#2026";
    private static final String SA_ID = "0001015009087";
    private String adminUser;

    @BeforeEach
    void seed() {
        rateLimiter.reset();
        adminUser = "admin-" + System.nanoTime();
        adminRepository.save(Admin.builder().username(adminUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
    }

    private String adminAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((adminUser + ":" + PW).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode createAdvert(String name, String status, LocalDate closingDate) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("name", name);
            put("qualificationCode", "48573");
            put("status", status);
            put("seta", "MICT SETA");
            put("nqfLevel", 4);
            put("durationMonths", 12);
            put("stipend", 3500);
            put("intake", "2027 Intake A");
            put("closingDate", closingDate == null ? null : closingDate.toString());
            put("description", "Learn IT support on the job.");
            put("requirements", "Grade 12\n\nAged 18 to 35\n");
        }});
        MvcResult result = mockMvc.perform(post("/api/admin/learnerships")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockMultipartHttpServletRequestBuilder application(String slug, String idNumber) {
        return application(slug, idNumber, java.util.Map.of());
    }

    /** A complete, valid application form; {@code overrides} replaces individual fields. */
    private MockMultipartHttpServletRequestBuilder application(String slug, String idNumber,
                                                               java.util.Map<String, String> overrides) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("learnershipSlug", slug);
        fields.put("idType", "South African ID");
        fields.put("idNumber", idNumber);
        fields.put("firstNames", "Amanda");
        fields.put("surname", "Mndawe");
        fields.put("email", "Amanda@example.com");
        fields.put("phone", "071 234 5678");
        fields.put("town", "Nelspruit");
        fields.put("province", "Mpumalanga");
        fields.put("highestGrade", "Grade 12");
        fields.put("dateOfBirth", "2000-01-01");
        fields.put("popiaConsent", "true");
        fields.putAll(overrides);

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/applications");
        builder.file(new MockMultipartFile("idCopy", "id.pdf", "application/pdf", "%PDF-1.4 id".getBytes()));
        builder.file(new MockMultipartFile("results", "results.pdf", "application/pdf", "%PDF-1.4 results".getBytes()));
        fields.forEach(builder::param);
        return builder;
    }

    private String submit(String slug, String idNumber) throws Exception {
        MvcResult result = mockMvc.perform(application(slug, idNumber))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", startsWith("ADM-")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("reference").asText();
    }

    @Test
    @DisplayName("Only open adverts whose closing date has not passed are listed publicly")
    void publicListingShowsOnlyOpenAdverts() throws Exception {
        JsonNode open = createAdvert("IT Systems Support NQF4", "OPEN", LocalDate.now().plusDays(30));
        createAdvert("Draft Learnership", "DRAFT", null);
        createAdvert("Closed Learnership", "CLOSED", null);

        assertThat(open.get("slug").asText()).isEqualTo("it-systems-support-nqf4");

        mockMvc.perform(get("/api/learnerships/openings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("IT Systems Support NQF4")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Draft Learnership"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Closed Learnership"))))
                .andExpect(jsonPath("$[0].requirements", contains("Grade 12", "Aged 18 to 35")))
                .andExpect(jsonPath("$[0].applicationCounts").doesNotExist());

        mockMvc.perform(get("/api/learnerships/openings/it-systems-support-nqf4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stipend").value(3500));
        mockMvc.perform(get("/api/learnerships/openings/draft-learnership"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An advert can't be opened with a closing date in the past")
    void openAdvertWithPastClosingDateIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/learnerships")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Late\",\"status\":\"OPEN\",\"closingDate\":\"" + LocalDate.now().minusDays(2) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Applying, checking status, moving through selection and enrolling")
    void fullAdmissionsFlow() throws Exception {
        createAdvert("IT Systems Support NQF4", "OPEN", LocalDate.now().plusDays(30));
        String reference = submit("it-systems-support-nqf4", SA_ID);

        // Applying twice for the same learnership is refused.
        mockMvc.perform(application("it-systems-support-nqf4", SA_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already applied")));

        // The status check needs both the reference and the ID number.
        mockMvc.perform(post("/api/applications/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"" + reference + "\",\"idNumber\":\"9999999999999\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/applications/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"" + reference.toLowerCase() + "\",\"idNumber\":\"" + SA_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline", hasSize(5)))
                .andExpect(jsonPath("$.timeline[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.staffNotes").doesNotExist());

        Long id = applicationRepository.findByReference(reference).orElseThrow().getId();

        // Staff see it, with its documents.
        mockMvc.perform(get("/api/admin/applications").header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value(reference))
                .andExpect(jsonPath("$[0].documentCount").value(2));
        MvcResult detail = mockMvc.perform(get("/api/admin/applications/" + id).header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)))
                .andExpect(jsonPath("$.summary.email").value("amanda@example.com"))
                .andReturn();
        long documentId = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("documents").get(0).get("id").asLong();
        mockMvc.perform(get("/api/admin/applications/" + id + "/documents/" + documentId + "/view")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        // Enrolling before acceptance is refused.
        mockMvc.perform(post("/api/admin/applications/" + id + "/enrol").header("Authorization", adminAuth()))
                .andExpect(status().isBadRequest());

        // Shortlist, then accept.
        mockMvc.perform(post("/api/admin/applications/" + id + "/status").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\",\"note\":\"Strong results\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));
        mockMvc.perform(post("/api/admin/applications/" + id + "/status").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());
        // ENROLLED can't be set by hand.
        mockMvc.perform(post("/api/admin/applications/" + id + "/status").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENROLLED\"}"))
                .andExpect(status().isBadRequest());

        // Enrol: a learner is created on the learnership, with the documents carried across.
        MvcResult enrolled = mockMvc.perform(post("/api/admin/applications/" + id + "/enrol")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerCode", matchesPattern("\\d{9}")))
                .andExpect(jsonPath("$.cohort").value("2027 Intake A"))
                .andExpect(jsonPath("$.documentsCopied").value(2))
                .andReturn();
        String learnerCode = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("learnerCode").asText();

        Learner learner = learnerRepository.findByLearnerCode(learnerCode).orElseThrow();
        assertThat(learner.getIdNumber()).isEqualTo(SA_ID);
        assertThat(learner.getFullName()).isEqualTo("Amanda Mndawe");
        assertThat(learner.getLearnership().getName()).isEqualTo("IT Systems Support NQF4");
        assertThat(learner.getPasswordHash()).isNull();
        List<LearnerDocument> documents = learnerDocumentRepository.findAll().stream()
                .filter(d -> d.getLearner().getId().equals(learner.getId())).toList();
        assertThat(documents).extracting(LearnerDocument::getDocumentType)
                .containsExactlyInAnyOrder(PoeDocumentType.ID_COPY, PoeDocumentType.MATRIC);

        // Enrolled is final.
        mockMvc.perform(post("/api/admin/applications/" + id + "/enrol").header("Authorization", adminAuth()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/applications/" + id + "/status").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/applications/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"" + reference + "\",\"idNumber\":\"" + SA_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                // Shortlisting implies screening, so that stage reads as done; the interview
                // stage was never used, and says so.
                .andExpect(jsonPath("$.timeline[1].state").value("DONE"))
                .andExpect(jsonPath("$.timeline[2].state").value("DONE"))
                .andExpect(jsonPath("$.timeline[3].state").value("SKIPPED"))
                .andExpect(jsonPath("$.timeline[4].state").value("DONE"));

        // A learnership with applications can't be deleted.
        Long learnershipId = learner.getLearnership().getId();
        mockMvc.perform(delete("/api/admin/learnerships/" + learnershipId).header("Authorization", adminAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Applications are refused when the advert is not open, lack consent or an ID copy")
    void invalidApplicationsAreRefused() throws Exception {
        createAdvert("Draft Only", "DRAFT", null);
        mockMvc.perform(application("draft-only", SA_ID)).andExpect(status().isBadRequest());

        createAdvert("Open One", "OPEN", null);
        mockMvc.perform(application("open-one", SA_ID, java.util.Map.of("popiaConsent", "false")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(application("open-one", "12345")).andExpect(status().isBadRequest());

        MockMultipartHttpServletRequestBuilder noId = multipart("/api/applications");
        noId.param("learnershipSlug", "open-one").param("idType", "Passport").param("idNumber", "A1234567")
                .param("firstNames", "Bongani").param("surname", "Sithole")
                .param("email", "b@example.com").param("phone", "0712345678").param("popiaConsent", "true");
        mockMvc.perform(noId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ID or passport")));

        MockMultipartHttpServletRequestBuilder badFile = application("open-one", SA_ID);
        badFile.file(new MockMultipartFile("cv", "cv.exe", "application/octet-stream", "MZ".getBytes()));
        mockMvc.perform(badFile).andExpect(status().isBadRequest());

        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    @DisplayName("The honeypot quietly drops bot submissions")
    void honeypotDropsSubmission() throws Exception {
        createAdvert("Open One", "OPEN", null);
        mockMvc.perform(application("open-one", SA_ID, java.util.Map.of("website", "http://spam.example")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", startsWith("ADM-")));
        assertThat(applicationRepository.count()).isZero();
    }

    @Test
    @DisplayName("Submitting is rate limited per caller")
    void submissionsAreRateLimited() throws Exception {
        createAdvert("Open One", "OPEN", null);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(application("open-one", "123").with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(application("open-one", SA_ID).with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("The admissions endpoints are admin-only")
    void adminEndpointsRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/applications")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/learnerships/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bulk status changes report what they skipped, and the CSV export escapes formulas")
    void bulkStatusAndExport() throws Exception {
        createAdvert("Open One", "OPEN", null);
        String first = submit("open-one", SA_ID);
        mockMvc.perform(application("open-one", "0001015009088", java.util.Map.of("firstNames", "=HYPERLINK(\"x\")"))).andExpect(status().isCreated());

        List<Long> ids = applicationRepository.findAll().stream().map(LearnershipApplication::getId).toList();
        mockMvc.perform(post("/api/admin/applications/bulk-status").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("ids", ids, "status", "SCREENING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        MvcResult csv = mockMvc.perform(get("/api/admin/applications/export.csv").header("Authorization", adminAuth()))
                .andExpect(status().isOk()).andReturn();
        String body = csv.getResponse().getContentAsString();
        assertThat(body).contains(first).contains("\"'=HYPERLINK(\"\"x\"\")\"");
    }
}
