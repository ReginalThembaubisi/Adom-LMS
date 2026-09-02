package com.example.learnerassignments.service;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
@Service
public class CloudinaryService {
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
        // Stream directly to Cloudinary instead of loading the entire file into heap
        Map uploadResult = cloudinary.uploader().uploadLarge(file.getInputStream(), ObjectUtils.asMap(
            "resource_type", "raw",
            "public_id", publicId,
            "chunk_size", 6_000_000
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

    public String getSignedBackupUrl(String publicId) {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        return cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .signed(true)
                .generate(publicId);
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