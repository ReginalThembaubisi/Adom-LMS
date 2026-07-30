package com.example.learnerassignments.service;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
@Service
public class CloudinaryService {
    private final Cloudinary cloudinary;
    public CloudinaryService(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
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
    public String uploadFile(MultipartFile file) throws IOException {
        if (this.cloudinary == null) {
            throw new IllegalStateException("Cloudinary is not configured. Please set Cloudinary environment variables.");
        }
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
            "resource_type", "raw",
            "public_id", "lms_files/" + System.currentTimeMillis() + "_" + file.getOriginalFilename().replaceAll("[^6-DZa-z0-9.-]", "_")
        ));
        return (String) uploadResult.get("secure_url");
    }
}