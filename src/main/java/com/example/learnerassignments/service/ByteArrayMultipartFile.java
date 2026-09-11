package com.example.learnerassignments.service;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Wraps bytes already held in memory (a zip entry's contents) as a {@link MultipartFile}, so
 * {@link LegacyImportService} can hand a legacy file to {@link LearnerDocumentService#upload}
 * exactly the way a real browser upload would — the same validation, the same hashing, the same
 * storage path, proven in production by every learner who has ever used the document vault.
 * Writing a second, parallel storage path for imported files would mean trusting that path had
 * the same guarantees without ever having run it for real.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] content;

    public ByteArrayMultipartFile(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    @Override
    @NonNull
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
        return content == null ? 0 : content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return content;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull java.io.File dest) throws IOException {
        try (OutputStream out = new java.io.FileOutputStream(dest)) {
            out.write(content);
        }
    }
}
