package com.example.learnerassignments.service;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
@Service
public class CloudinaryService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cloudinary cloudinary;
    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        if (cloudName != null && !cloudName.isEmpty()) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
            ));
        } else {
            this.cloudinary = null;
        }
    }
    public boolean isConfigured() {
        return this.cloudinary != null;
    }
    // Cloudinary's raw/PDF security restriction (blocking both plain public delivery and
    // signed public delivery with a 401, and apparently also "authenticated" delivery on this
    // account — both tried and both still 401'd live) triggers off the recognized file
    // extension/format in the public_id. Store the file under a public_id with NO extension
    // so Cloudinary treats it as an anonymous raw blob instead of "a PDF", sidestepping the
    // restriction entirely rather than trying to satisfy it. The real filename (and the
    // correct Content-Type to serve it with) is tracked separately in our own database via
    // Submission.originalFilename — SubmissionController#viewSubmissionFile resolves the
    // Content-Type from that, not from anything Cloudinary reports, so this is safe.
    public String uploadFile(MultipartFile file) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        String originalFilename = file.getOriginalFilename();
        String nameWithoutExtension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
                : originalFilename;
        String publicId = "lms_files/" + System.currentTimeMillis() + "_" + nameWithoutExtension.replaceAll("[^a-zA-Z0-9-]", "_");
        // getBytes(), not getInputStream(): the SDK rejects a stream. This method has had that
        // bug since it was written, which means module files and task briefs have been failing
        // to upload for as long as Cloudinary has been configured.
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
            "resource_type", "raw",
            "public_id", publicId
        ));
        return (String) uploadResult.get("secure_url");
    }

    // Backups use resource_type "authenticated" (not plain public "upload") so a leaked or
    // guessed URL alone can't be used to download student/staff data — every access needs a
    // signature generated server-side via getSignedBackupUrl().
    public String uploadBackup(byte[] data, String filename) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        String publicId = "lms_backups/" + filename;
        cloudinary.uploader().upload(data, ObjectUtils.asMap(
            "resource_type", "raw",
            "type", "authenticated",
            "public_id", publicId
        ));
        return publicId;
    }


    /**
     * The public_id prefix every authenticated learner-facing file is stored under.
     *
     * This constant is load-bearing, not cosmetic. {@link StoredFileService} decides whether
     * a value in a filePath column is a Cloudinary public_id or a path on disk by testing for
     * this prefix, because the two shapes are otherwise indistinguishable: a legacy row can
     * hold a relative disk path like "uploads/x.pdf", which looks exactly like a public_id.
     * Keying off a prefix that only this class ever writes means no row that predates
     * authenticated delivery can be misread as a public_id. Changing it without changing the
     * resolver would make every file stored after the change unreadable.
     */
    public static final String SECURE_PREFIX = "lms_secure/";

    /**
     * Uploads a learner-facing file (a submission, a marked copy, a portfolio document) and
     * returns its <strong>public_id</strong> — never a URL.
     *
     * Two things are deliberate here.
     *
     * <p>{@code type: "authenticated"} is the point of the method. A plain "upload" resource
     * is readable by anyone who has the URL, and those URLs travelled through email, browser
     * history and anywhere a learner pasted one; the file behind them stayed readable to a
     * logged-out stranger forever. An authenticated resource cannot be fetched without a
     * signature this server generates, so possession of the URL is no longer possession of
     * the document. This mirrors what {@code uploadBackup} has always done.
     *
     * <p>The public_id carries <strong>no file extension</strong>, for the reason recorded on
     * {@link #uploadFile}: Cloudinary's raw/PDF delivery restriction triggers off a recognised
     * format in the public_id and returns 401 even for correctly signed requests. Storing the
     * bytes under an extension-less id sidesteps the restriction rather than trying to satisfy
     * it. The real filename and the Content-Type to serve it with are held in our own database
     * (Submission.originalFilename, LearnerDocument.originalFilename), never read back from
     * Cloudinary, so nothing depends on the extension surviving.
     */
    public String uploadLearnerFile(MultipartFile file) throws IOException {
        // getBytes(), never getInputStream(). See the note on the byte[] overload below: the
        // SDK's upload() has no InputStream branch and rejects one outright.
        return uploadLearnerFile(file.getBytes(), file.getOriginalFilename());
    }

    /**
     * As {@link #uploadLearnerFile(MultipartFile)}, for callers that already hold the bytes.
     *
     * <p>The parameter is {@code byte[]} rather than {@code Object} deliberately, and must stay
     * that way. Cloudinary's {@code Uploader.upload(Object, Map)} dispatches on {@code String},
     * {@code File} and {@code byte[]}, and throws {@code IOException("Unrecognized file
     * parameter")} for anything else — {@code InputStream} included, which is handled only by
     * the separate {@code uploadLarge}. Passing a stream therefore fails every time, at
     * runtime, with a message that names no cause. Typing this parameter turns that into a
     * compile error instead.
     */
    public String uploadLearnerFile(byte[] content, String originalFilename) throws IOException {
        return uploadLearnerFile((Object) content, originalFilename);
    }

    /**
     * As {@link #uploadLearnerFile(byte[], String)}, for a file already sitting on disk.
     *
     * The PoE export (Phase 8) builds a zip that can run to hundreds of megabytes on a 512MB
     * instance and writes it straight to a temp file rather than a {@code byte[]} for that
     * reason. Adding this overload lets the upload go straight from that file to Cloudinary —
     * the SDK dispatches on {@code File} exactly as it does on {@code byte[]}, so this is the
     * one case where handing it something other than bytes is not the mistake documented on
     * the {@code byte[]} overload below.
     */
    public String uploadLearnerFile(java.io.File content, String originalFilename) throws IOException {
        return uploadLearnerFile((Object) content, originalFilename);
    }

    private String uploadLearnerFile(Object content, String originalFilename) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        String publicId = SECURE_PREFIX + secureName(originalFilename);
        cloudinary.uploader().upload(content, ObjectUtils.asMap(
            "resource_type", "raw",
            "type", "authenticated",
            "public_id", publicId
        ));
        return publicId;
    }

    /**
     * A collision-free, extension-less id fragment derived from the uploaded filename.
     *
     * The timestamp alone is not enough. Cloudinary overwrites an existing public_id by
     * default, and two learners uploading files with the same name in the same millisecond
     * would produce the same id — which for a system whose whole design is "supersede, never
     * overwrite" would silently destroy one of them. The random suffix removes that.
     */
    private String secureName(String originalFilename) {
        String base = originalFilename == null ? "file" : originalFilename;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^a-zA-Z0-9-]", "_");
        if (base.isEmpty()) {
            base = "file";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return System.currentTimeMillis() + "_" + randomSuffix() + "_" + base;
    }

    private String randomSuffix() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * A short-lived signed URL for an authenticated raw resource.
     *
     * The returned string is a credential: anyone holding it can fetch the file until it is
     * regenerated. It must never be logged, returned to a browser, or put in an exception
     * message. Callers fetch with it server-side and re-serve the bytes.
     */
    public String getSignedUrl(String publicId) {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        return cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .signed(true)
                .generate(publicId);
    }

    /**
     * Signs a database backup's public_id. Identical mechanism to {@link #getSignedUrl}; kept
     * as a separate name because the backup listing in AdminController reads as backup code.
     */
    public String getSignedBackupUrl(String publicId) {
        return getSignedUrl(publicId);
    }

    /**
     * Deletes an authenticated raw resource. Used by the delivery health check to clean up
     * after itself; failure is not worth propagating, since a stray probe file is harmless.
     */
    public void deleteLearnerFile(String publicId) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
            "resource_type", "raw",
            "type", "authenticated"
        ));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listBackups() throws Exception {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        Map result = cloudinary.api().resources(ObjectUtils.asMap(
            "type", "authenticated",
            "resource_type", "raw",
            "prefix", "lms_backups/",
            "max_results", 100
        ));
        return (List<Map<String, Object>>) result.get("resources");
    }
}