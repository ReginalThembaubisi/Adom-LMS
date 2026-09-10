package com.example.learnerassignments.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The check is only worth having if the log distinguishes every outcome, so every outcome is
 * asserted on the log line rather than on a return value — the log line is the whole product.
 */
class DeliveryHealthCheckTest {

    private CloudinaryService cloudinary;
    private StoredFileService storedFiles;
    private DeliveryHealthCheck check;
    private ListAppender<ILoggingEvent> logged;
    private Logger logger;

    @BeforeEach
    void setUp() {
        cloudinary = mock(CloudinaryService.class);
        storedFiles = mock(StoredFileService.class);
        check = new DeliveryHealthCheck(cloudinary, storedFiles);
        ReflectionTestUtils.setField(check, "enabled", true);

        logger = (Logger) LoggerFactory.getLogger(DeliveryHealthCheck.class);
        logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logged);
    }

    @Test
    @DisplayName("A working round trip says so, and cleans up after itself")
    void passingRoundTripIsLogged() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenAnswer(invocation -> {
            uploaded = (byte[]) invocation.getArgument(0);
            return "lms_secure/1730_probe";
        });
        when(storedFiles.readBytes(eq("lms_secure/1730_probe"), anyString()))
                .thenAnswer(invocation -> uploaded);

        check.run();

        assertThat(messages()).anyMatch(m -> m.contains("passed"));
        assertThat(levels()).doesNotContain(Level.ERROR);
        // A probe file left behind on every boot would accumulate one object per cold start.
        verify(cloudinary).deleteLearnerFile("lms_secure/1730_probe");
    }

    private byte[] uploaded;

    @Test
    @DisplayName("A signed fetch that fails says what it means for the vault, not just that it failed")
    void failingFetchIsLoggedWithItsConsequence() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenReturn("lms_secure/1730_probe");
        when(storedFiles.readBytes(anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("could not be retrieved from storage"));

        check.run();

        String error = messages().stream().filter(m -> m.contains("FAILED")).findFirst().orElseThrow();
        // This is the documented past failure on this account: uploads succeed, delivery 401s,
        // and nothing else in the system notices. The message has to name the step, because a
        // failed upload and a failed read are opposite operational states — one is visible to
        // the learner immediately, this one is not visible to anybody.
        assertThat(error).contains("uploaded fine but could not be read back");
        assertThat(error).contains("will be accepted and stored but will not open");
        assertThat(error).contains("Do not announce");
        // Legacy files are served by a different path and are genuinely unaffected; saying so
        // stops this line from reading as a total outage.
        assertThat(error).contains("unaffected");
        verify(cloudinary).deleteLearnerFile("lms_secure/1730_probe");
    }

    @Test
    @DisplayName("Bytes that come back different are a failure, not a pass")
    void mismatchedBytesFail() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenReturn("lms_secure/1730_probe");
        when(storedFiles.readBytes(anyString(), anyString()))
                .thenReturn("something else entirely".getBytes(StandardCharsets.UTF_8));

        check.run();

        assertThat(messages()).anyMatch(m -> m.contains("FAILED") && m.contains("not the bytes"));
    }

    @Test
    @DisplayName("An uploader that goes back to returning URLs is caught")
    void aUrlFromTheUploaderIsAFailure() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString()))
                .thenReturn("https://res.cloudinary.com/x/raw/upload/lms_files/y");

        check.run();

        // The regression Phase 4 exists to prevent, caught at boot rather than in review.
        assertThat(messages()).anyMatch(m -> m.contains("FAILED") && m.contains("publicly readable"));
    }

    @Test
    @DisplayName("An upload that throws does not take the application down with it")
    void anUploadFailureIsContained() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString()))
                .thenThrow(new java.io.IOException("connection reset"));

        check.run();

        // Named as an upload failure, not a delivery failure: a learner attempting an upload
        // sees an error, so nothing is silently accumulating and the response is different.
        assertThat(messages()).anyMatch(m -> m.contains("FAILED") && m.contains("could not be uploaded"));
        assertThat(messages()).noneMatch(m -> m.contains("will not open"));
        // Nothing was stored, so there is nothing to clean up and no second confusing error.
        verify(cloudinary, never()).deleteLearnerFile(anyString());
    }

    @Test
    @DisplayName("A cleanup that fails does not turn a pass into a failure")
    void cleanupFailureDoesNotMaskAPass() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenAnswer(invocation -> {
            uploaded = (byte[]) invocation.getArgument(0);
            return "lms_secure/1730_probe";
        });
        when(storedFiles.readBytes(anyString(), anyString())).thenAnswer(invocation -> uploaded);
        doThrow(new java.io.IOException("destroy failed")).when(cloudinary).deleteLearnerFile(anyString());

        check.run();

        assertThat(messages()).anyMatch(m -> m.contains("passed"));
        assertThat(levels()).doesNotContain(Level.ERROR);
    }

    @Test
    @DisplayName("Not being configured is reported, not passed over in silence")
    void unconfiguredIsStillALogLine() {
        when(cloudinary.isConfigured()).thenReturn(false);

        check.scheduleCheck();

        // "Check the logs" is only advice if the logs can tell the states apart. Silence here
        // would be indistinguishable from the check never having run.
        assertThat(messages()).anyMatch(m -> m.contains("skipped") && m.contains("not configured"));
    }

    @Test
    @DisplayName("Being switched off is reported too")
    void disabledIsStillALogLine() {
        ReflectionTestUtils.setField(check, "enabled", false);

        check.scheduleCheck();

        assertThat(messages()).anyMatch(m -> m.contains("skipped") && m.contains("health-check.enabled"));
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<Level> levels() {
        return logged.list.stream().map(ILoggingEvent::getLevel).toList();
    }

    // --- the on-demand path, which is what an admin will actually use to diagnose this ---

    @Test
    @DisplayName("checkNow reports the storage provider's own error, not the wrapper's class name")
    void checkNowSurfacesTheProviderMessage() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString()))
                .thenThrow(new java.io.IOException("Invalid Signature abc123. String to sign - 'public_id=...'"));

        DeliveryHealthCheck.Result result = check.checkNow();

        // "IOException" tells an admin nothing. Cloudinary's own words are the whole diagnosis.
        assertThat(result.ok()).isFalse();
        assertThat(result.step()).isEqualTo("upload");
        assertThat(result.detail()).contains("Invalid Signature");
    }

    @Test
    @DisplayName("checkNow unwraps a cause, since the SDK usually wraps the real error")
    void checkNowUnwrapsCauses() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenThrow(
                new java.io.IOException("upload failed", new IllegalStateException("Untrusted customer")));

        DeliveryHealthCheck.Result result = check.checkNow();

        assertThat(result.detail()).contains("upload failed");
        assertThat(result.detail()).contains("Untrusted customer");
    }

    @Test
    @DisplayName("checkNow names the delivery step when storing worked and reading did not")
    void checkNowDistinguishesDeliveryFromUpload() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenReturn("lms_secure/1730_probe");
        when(storedFiles.readBytes(anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("could not be retrieved from storage"));

        DeliveryHealthCheck.Result result = check.checkNow();

        // Upload and delivery fail for different reasons and need different fixes; an admin
        // staring at one message needs to know which half is broken.
        assertThat(result.step()).isEqualTo("delivery");
    }

    @Test
    @DisplayName("checkNow says so when there is no Cloudinary rather than reporting a pass")
    void checkNowReportsMissingConfiguration() {
        when(cloudinary.isConfigured()).thenReturn(false);

        DeliveryHealthCheck.Result result = check.checkNow();

        assertThat(result.ok()).isFalse();
        assertThat(result.step()).isEqualTo("configuration");
        assertThat(result.detail()).contains("not configured");
    }

    @Test
    @DisplayName("A passing check reports it, so a green answer is distinguishable from a silent one")
    void checkNowReportsSuccess() throws Exception {
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.uploadLearnerFile(any(), anyString())).thenAnswer(invocation -> {
            uploaded = (byte[]) invocation.getArgument(0);
            return "lms_secure/1730_probe";
        });
        when(storedFiles.readBytes(anyString(), anyString())).thenAnswer(invocation -> uploaded);

        DeliveryHealthCheck.Result result = check.checkNow();

        assertThat(result.ok()).isTrue();
        assertThat(result.step()).isEqualTo("complete");
    }
}
