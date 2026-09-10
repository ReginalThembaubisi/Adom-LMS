package com.example.learnerassignments.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two acceptance criteria for authenticated delivery that can be checked without a
 * Cloudinary account: what gets stored, and what gets sent.
 *
 * The upload is mocked because there is no account in CI, but the arguments are real — the
 * options map asserted here is exactly the one the SDK receives.
 */
class CloudinaryAuthenticatedUploadTest {

    private CloudinaryService service;
    private Uploader uploader;

    @BeforeEach
    void setUp() {
        service = new CloudinaryService("test-cloud", "key", "secret");
        Cloudinary cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        ReflectionTestUtils.setField(service, "cloudinary", cloudinary);
    }

    @Test
    @DisplayName("What is stored is a public_id, never a URL")
    void storesAPublicIdNotAUrl() throws Exception {
        when(uploader.upload(any(), any())).thenReturn(new HashMap<>(Map.of(
                "secure_url", "https://res.cloudinary.com/test-cloud/raw/authenticated/lms_secure/x")));

        String stored = service.uploadLearnerFile(
                new MockMultipartFile("file", "My CV.pdf", "application/pdf", "bytes".getBytes()));

        // The whole point of the phase: the row holds an identifier that is useless on its
        // own, not a link that opens the document for anyone who has it.
        assertThat(stored).doesNotStartWith("http");
        assertThat(stored).startsWith(CloudinaryService.SECURE_PREFIX);
    }

    @Test
    @DisplayName("The upload is an authenticated resource, under an extension-less id")
    void uploadsAsAuthenticatedWithoutAnExtension() throws Exception {
        when(uploader.upload(any(), any())).thenReturn(new HashMap<String, Object>());

        service.uploadLearnerFile(
                new MockMultipartFile("file", "My CV.pdf", "application/pdf", "bytes".getBytes()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(uploader).upload(any(), options.capture());

        assertThat(options.getValue()).containsEntry("type", "authenticated");
        assertThat(options.getValue()).containsEntry("resource_type", "raw");

        // No extension in the public_id. Cloudinary's raw/PDF delivery restriction triggers
        // off a recognised format and 401s even a correctly signed request; an extension here
        // would make every new file unreadable, which is exactly the failure the comment on
        // uploadFile records having hit twice already.
        String publicId = (String) options.getValue().get("public_id");
        assertThat(publicId).doesNotContain(".");
        assertThat(publicId).startsWith(CloudinaryService.SECURE_PREFIX);
    }

    @Test
    @DisplayName("Two files with the same name do not collide onto one public_id")
    void identicalFilenamesGetDistinctIds() throws Exception {
        when(uploader.upload(any(), any())).thenReturn(new HashMap<String, Object>());

        // Cloudinary overwrites a public_id that already exists. In a system whose design is
        // "supersede, never overwrite", a collision would destroy the earlier version's bytes
        // while the row still claimed to point at them.
        String first = service.uploadLearnerFile(
                new MockMultipartFile("file", "ID.pdf", "application/pdf", "one".getBytes()));
        String second = service.uploadLearnerFile(
                new MockMultipartFile("file", "ID.pdf", "application/pdf", "two".getBytes()));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("A file with no usable name still gets a valid id")
    void handlesAMissingFilename() throws Exception {
        when(uploader.upload(any(), any())).thenReturn(new HashMap<String, Object>());

        // The pre-existing uploadFile would throw NullPointerException here.
        String stored = service.uploadLearnerFile(
                new MockMultipartFile("file", null, "application/pdf", "bytes".getBytes()));

        assertThat(stored).startsWith(CloudinaryService.SECURE_PREFIX);
        assertThat(stored).doesNotEndWith("_");
    }
}
