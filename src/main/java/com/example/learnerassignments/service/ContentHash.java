package com.example.learnerassignments.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 of a file's bytes, taken as it is uploaded.
 *
 * Recorded at upload rather than derived later so that Phase 9 can sign a document without
 * fetching it, and so a signature can be checked afterwards against what was actually
 * received. A hash computed later, from a file downloaded later, proves only what that file
 * is now — which is the opposite of what a signature needs.
 */
public final class ContentHash {

    private ContentHash() {
    }

    /** Hex SHA-256 of the upload, or null if it cannot be read — never a reason to fail the upload. */
    public static String of(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            return of(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Hex SHA-256 of a byte array. */
    public static String of(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private static String of(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Streamed rather than via getBytes(): a 20 MB submission does not need to be held
            // in memory twice just to be measured.
            try (DigestInputStream digestStream = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (digestStream.read(buffer) != -1) {
                    // Reading is the work; DigestInputStream updates the digest as it goes.
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
