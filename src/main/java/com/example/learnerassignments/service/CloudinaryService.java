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
    // Submission files use resource_type "raw" + type "authenticated" — the same pattern
    // already proven for backups below. Cloudinary's account-level security setting blocks
    // *public* ("upload" type) delivery of raw/PDF files with a 401 regardless of whether the
    // URL carries a signature — signing a public-type URL does not bypass that restriction,
    // only actually uploading (and delivering) as "authenticated" does.
    public String uploadFile(MultipartFile file) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        String publicId = "lms_files/" + System.currentTimeMillis() + "_" + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9.-]", "_");
        cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
            "resource_type", "raw",
            "type", "authenticated",
            "public_id", publicId
        ));
        return getSignedFileUrl(publicId);
    }

    public String getSignedFileUrl(String publicId) {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        return cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .signed(true)
                .generate(publicId);
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