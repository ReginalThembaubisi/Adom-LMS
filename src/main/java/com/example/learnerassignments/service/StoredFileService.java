package com.example.learnerassignments.service;

import com.example.learnerassignments.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;

/**
 * Reads a file back from wherever its {@code filePath} column says it lives.
 *
 * <p>Three storage shapes coexist in this database and will keep coexisting; there is no
 * migration that collapses them, by design. Re-uploading 298 production submissions to change
 * where they sit would mean downloading and re-storing other people's assessment evidence in
 * bulk for no benefit the learner can see, and any failure part-way through leaves half the
 * cohort's work unreachable. Reading both shapes is cheaper and cannot break what already
 * works.
 *
 * <ul>
 *   <li><strong>Legacy public URL</strong> — a Cloudinary {@code secure_url} written before
 *       authenticated delivery. Fetched as-is, unsigned, because it is a public resource.
 *       These stay readable by anyone holding the URL; that is exactly the exposure this phase
 *       stops creating, and it cannot be retracted for files already stored that way without
 *       re-uploading them.</li>
 *   <li><strong>Cloudinary public_id</strong> — everything written from this phase onward,
 *       always prefixed {@link CloudinaryService#SECURE_PREFIX}. Signed server-side, fetched,
 *       re-served.</li>
 *   <li><strong>Local disk path</strong> — the fallback used when Cloudinary is not
 *       configured, and where the legacy file migration relocated submissions out of the
 *       publicly served directory.</li>
 * </ul>
 *
 * <p>The classification rule is deliberately conservative. It does <em>not</em> say "anything
 * that is not a URL is a public_id", because a stored path can legitimately be relative —
 * {@code LegacySubmissionFileMigration} resolves exactly that case — and {@code uploads/x.pdf}
 * is shaped identically to a public_id. Instead a value is treated as a public_id only when it
 * carries the prefix that only {@link CloudinaryService#uploadLearnerFile} ever writes. Every
 * row that predates this phase therefore keeps resolving the way it did before, which is the
 * property that matters: this phase changes how files are stored, not whether stored files
 * still open.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StoredFileService {

    private final CloudinaryService cloudinaryService;

    /** How a value in a filePath column should be read back. */
    public enum Shape {
        /** Empty or null — the row has no file. */
        MISSING,
        /** A pre-Phase-4 public Cloudinary URL. Fetched without a signature. */
        LEGACY_URL,
        /** A public_id for an authenticated resource. Signed, then fetched. */
        CLOUDINARY_PUBLIC_ID,
        /** A path on this machine's filesystem. */
        LOCAL_PATH
    }

    public Shape classify(String filePath) {
        return classify(filePath, cloudinaryService.isConfigured());
    }

    /**
     * The classification rule, with the Cloudinary-configured flag passed in so it can be
     * exercised both ways without a Cloudinary account.
     *
     * <p>Order matters. A public_id is recognised by prefix before anything else non-URL, and
     * the prefix check runs only when Cloudinary is configured — on a deployment with no
     * Cloudinary at all, no public_id can exist and a value shaped like one is far more likely
     * to be a directory someone named unluckily than a resource we could ever fetch.
     */
    static Shape classify(String filePath, boolean cloudinaryConfigured) {
        if (filePath == null || filePath.isBlank()) {
            return Shape.MISSING;
        }
        String value = filePath.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return Shape.LEGACY_URL;
        }
        if (cloudinaryConfigured && value.startsWith(CloudinaryService.SECURE_PREFIX)) {
            return Shape.CLOUDINARY_PUBLIC_ID;
        }
        return Shape.LOCAL_PATH;
    }

    /**
     * The file's contents, ready to hand to a {@code ResponseEntity} body.
     *
     * <p>Returns a streaming {@link Resource} for a local file and a {@code byte[]} for a
     * remote one, matching what the view endpoints already do. Remote files are fetched and
     * re-served rather than redirected to: a redirect would hand the browser a Cloudinary URL,
     * which for a signed URL means handing out a working credential, and for a legacy public
     * URL means the browser can be pointed at learner evidence with no session at all. It
     * would also lose the ownership check that just ran.
     *
     * @param filePath the stored value from a filePath column
     * @param description what to call this in an error a user might see, e.g. "submission"
     */
    public Object open(String filePath, String description) {
        Shape shape = classify(filePath);
        switch (shape) {
            case MISSING:
                throw new ResourceNotFoundException("This " + description + " has no file stored.");
            case LEGACY_URL:
                // Unsigned on purpose: this is a public resource, and signing a public
                // delivery URL makes Cloudinary reject it.
                return fetch(filePath, filePath, description);
            case CLOUDINARY_PUBLIC_ID:
                String signed = cloudinaryService.getSignedUrl(filePath);
                // The signed URL is passed for the request and the public_id for diagnostics.
                // A signed URL in a log line or an exception message is a credential someone
                // can copy out and use; the public_id on its own is useless without a
                // signature this server has to generate.
                return fetch(signed, filePath, description);
            case LOCAL_PATH:
            default:
                return openLocal(filePath, description);
        }
    }

    /** The same file as {@link #open}, always materialised as bytes. */
    public byte[] readBytes(String filePath, String description) {
        Object opened = open(filePath, description);
        if (opened instanceof byte[] bytes) {
            return bytes;
        }
        try {
            return ((Resource) opened).getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new ResourceNotFoundException("Could not read the stored " + description + ".");
        }
    }

    private Resource openLocal(String filePath, String description) {
        try {
            Path path = Paths.get(filePath).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Stored {} is missing from disk at {}.", description, path);
                throw new ResourceNotFoundException("This " + description + " is missing from storage.");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("This " + description + " is missing from storage.");
        }
    }

    /**
     * Fetches remote bytes.
     *
     * @param url        the URL to request, which may be signed and must therefore not be
     *                   logged or echoed anywhere
     * @param identifier the safe-to-log stand-in for that URL
     */
    private byte[] fetch(String url, String identifier, String description) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                // Cloudinary usually sends an empty body and puts the real reason for an edge
                // rejection in a header (x-cld-error). Log both, plus the identifier, so a
                // failure here is diagnosable — the previous version of this could only say
                // "it didn't work".
                String body = new String(response.body(), StandardCharsets.UTF_8);
                if (body.length() > 300) {
                    body = body.substring(0, 300);
                }
                String cldError = response.headers().firstValue("x-cld-error").orElse("");
                log.error("Storage returned {} for {} {} (x-cld-error={}, body={}).",
                        response.statusCode(), description, identifier, cldError, body);
                throw new ResourceNotFoundException(
                        "This " + description + " could not be retrieved from storage.");
            }
            return response.body();
        } catch (IOException e) {
            log.error("Could not fetch {} {} from storage.", description, identifier, e);
            throw new ResourceNotFoundException("This " + description + " could not be retrieved from storage.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceNotFoundException("This " + description + " could not be retrieved from storage.");
        }
    }
}
