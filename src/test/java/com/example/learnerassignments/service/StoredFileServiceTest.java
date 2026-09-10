package com.example.learnerassignments.service;

import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The classification rule is the single point where 298 production submissions either keep
 * opening or stop, so it is tested value by value rather than by example.
 *
 * The rule that actually protects those rows is the negative one: a value that is not a URL is
 * only a public_id if it carries the prefix this application writes. Anything else — including
 * a relative path, which is shaped identically to a public_id — stays a disk path and keeps
 * resolving the way it did before this phase.
 */
class StoredFileServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ---- classification -----------------------------------------------------------------

    @Test
    @DisplayName("A pre-Phase-4 Cloudinary secure_url is read as a legacy public URL")
    void legacyUrlsAreRecognised() {
        assertThat(StoredFileService.classify("https://res.cloudinary.com/x/raw/upload/lms_files/1_cv", true))
                .isEqualTo(StoredFileService.Shape.LEGACY_URL);
        assertThat(StoredFileService.classify("http://res.cloudinary.com/x/raw/upload/lms_files/1_cv", true))
                .isEqualTo(StoredFileService.Shape.LEGACY_URL);
        // Recognised regardless of Cloudinary being configured: a stored URL is still a URL on
        // a deployment that has since had its Cloudinary credentials removed.
        assertThat(StoredFileService.classify("https://res.cloudinary.com/x/a", false))
                .isEqualTo(StoredFileService.Shape.LEGACY_URL);
        // Scheme casing is not something a stored value is guaranteed to have got right.
        assertThat(StoredFileService.classify("HTTPS://res.cloudinary.com/x/a", true))
                .isEqualTo(StoredFileService.Shape.LEGACY_URL);
    }

    @Test
    @DisplayName("Only the prefix this application writes is read as a public_id")
    void publicIdsAreRecognisedByPrefix() {
        assertThat(StoredFileService.classify("lms_secure/1730000000000_ab12cd34ef56_cv", true))
                .isEqualTo(StoredFileService.Shape.CLOUDINARY_PUBLIC_ID);
    }

    @Test
    @DisplayName("A relative disk path is never mistaken for a public_id")
    void relativePathsStayLocal() {
        // This is the case that would silently break existing submissions if classification
        // were "not a URL means public_id". LegacySubmissionFileMigration resolves stored
        // paths that may be relative, so rows in this shape are known to exist.
        assertThat(StoredFileService.classify("uploads/LR001_3_essay.pdf", true))
                .isEqualTo(StoredFileService.Shape.LOCAL_PATH);
        assertThat(StoredFileService.classify("private-uploads/documents/LR001_CV_1730_id.pdf", true))
                .isEqualTo(StoredFileService.Shape.LOCAL_PATH);
        // Shaped exactly like a public_id, but under the prefix the old uploader used, which
        // was only ever embedded inside a secure_url and never stored bare.
        assertThat(StoredFileService.classify("lms_files/1730000000000_cv", true))
                .isEqualTo(StoredFileService.Shape.LOCAL_PATH);
    }

    @Test
    @DisplayName("An absolute disk path stays a disk path")
    void absolutePathsStayLocal() {
        assertThat(StoredFileService.classify("/app/private-uploads/LR001_3_essay.pdf", true))
                .isEqualTo(StoredFileService.Shape.LOCAL_PATH);
    }

    @Test
    @DisplayName("Without Cloudinary configured nothing resolves to a public_id")
    void noCloudinaryMeansNoPublicIds() {
        // No public_id can exist on a deployment that has never had Cloudinary, so a value
        // shaped like one is more plausibly a directory than a resource we could fetch.
        assertThat(StoredFileService.classify("lms_secure/1730000000000_ab12cd34ef56_cv", false))
                .isEqualTo(StoredFileService.Shape.LOCAL_PATH);
    }

    @Test
    @DisplayName("An absent file path is reported as absent, not as a broken read")
    void missingIsItsOwnShape() {
        assertThat(StoredFileService.classify(null, true)).isEqualTo(StoredFileService.Shape.MISSING);
        assertThat(StoredFileService.classify("", true)).isEqualTo(StoredFileService.Shape.MISSING);
        assertThat(StoredFileService.classify("   ", true)).isEqualTo(StoredFileService.Shape.MISSING);
    }

    // ---- reading ------------------------------------------------------------------------

    @Test
    @DisplayName("A legacy http path is fetched and re-served")
    void legacyUrlIsFetched() throws Exception {
        String url = serve("/legacy.pdf", "the original submission");
        StoredFileService service = serviceWith(false);

        Object body = service.open(url, "submission");

        assertThat(body).isInstanceOf(byte[].class);
        assertThat(new String((byte[]) body, StandardCharsets.UTF_8)).isEqualTo("the original submission");
    }

    @Test
    @DisplayName("A public_id is signed before it is fetched, and the signed URL is what gets requested")
    void publicIdIsSignedThenFetched() throws Exception {
        String signed = serve("/signed.pdf", "the authenticated submission");
        CloudinaryService cloudinary = mock(CloudinaryService.class);
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.getSignedUrl("lms_secure/1730_ab12_essay")).thenReturn(signed);

        Object body = new StoredFileService(cloudinary).open("lms_secure/1730_ab12_essay", "submission");

        assertThat(new String((byte[]) body, StandardCharsets.UTF_8)).isEqualTo("the authenticated submission");
    }

    @Test
    @DisplayName("A local file is streamed rather than read into memory")
    void localFileIsStreamed() throws Exception {
        Path file = Files.createTempFile("stored-file-test", ".pdf");
        Files.writeString(file, "on disk");

        Object body = serviceWith(false).open(file.toString(), "submission");

        assertThat(body).isInstanceOf(Resource.class);
        assertThat(((Resource) body).getInputStream().readAllBytes())
                .asString(StandardCharsets.UTF_8).isEqualTo("on disk");
        Files.deleteIfExists(file);
    }

    @Test
    @DisplayName("A failed fetch never puts the signed URL where someone could copy it out")
    void signedUrlNeverReachesTheCaller() throws Exception {
        String signed = serve("/gone.pdf", null); // responds 404
        CloudinaryService cloudinary = mock(CloudinaryService.class);
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.getSignedUrl("lms_secure/1730_ab12_essay")).thenReturn(signed);

        assertThatThrownBy(() -> new StoredFileService(cloudinary).open("lms_secure/1730_ab12_essay", "submission"))
                .isInstanceOf(ResourceNotFoundException.class)
                // A signed URL is a working credential for the file. The old fetch helper put
                // the requested URL straight into the exception message, which for an
                // authenticated resource would hand it to whoever triggered the failure.
                .hasMessageNotContaining(signed)
                .hasMessageNotContaining("http");
    }

    @Test
    @DisplayName("A row with no file says so instead of failing as a bad read")
    void missingFileIsReported() {
        assertThatThrownBy(() -> serviceWith(true).open(null, "document"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no file stored");
    }

    // ---- helpers ------------------------------------------------------------------------

    private StoredFileService serviceWith(boolean configured) {
        CloudinaryService cloudinary = mock(CloudinaryService.class);
        when(cloudinary.isConfigured()).thenReturn(configured);
        return new StoredFileService(cloudinary);
    }

    /** Serves one path on a real loopback HTTP server, so the fetch path is genuinely exercised. */
    private String serve(String path, String content) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
