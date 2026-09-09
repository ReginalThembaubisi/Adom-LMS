package com.example.learnerassignments.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Hashing, taken at upload so a signature can later be checked without fetching the file. */
class ContentHashTest {

    @Test
    @DisplayName("hashes match the published SHA-256 of the same bytes")
    void matchesKnownDigest() {
        // The SHA-256 of "abc", which is checkable against any other implementation.
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertThat(ContentHash.of("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(expected);
        assertThat(ContentHash.of(new MockMultipartFile("f", "f.pdf", "application/pdf",
                "abc".getBytes(StandardCharsets.UTF_8)))).isEqualTo(expected);
    }

    @Test
    @DisplayName("the same bytes hash the same way whichever door they come in")
    void streamAndArrayAgree() {
        byte[] bytes = "a longer document, of the sort a learner actually submits".getBytes(StandardCharsets.UTF_8);

        assertThat(ContentHash.of(new MockMultipartFile("f", "f.pdf", "application/pdf", bytes)))
                .isEqualTo(ContentHash.of(bytes));
    }

    @Test
    @DisplayName("different content hashes differently")
    void differentContentDiffers() {
        assertThat(ContentHash.of("one".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(ContentHash.of("two".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("nothing to hash is null, not a failure")
    void absentInputIsNull() {
        // A hash is worth having, but never worth failing an upload over.
        assertThat(ContentHash.of((byte[]) null)).isNull();
        assertThat(ContentHash.of(new MockMultipartFile("f", "f.pdf", "application/pdf", new byte[0]))).isNull();
    }

    @Test
    @DisplayName("a hash is 64 hex characters, which is what the column holds")
    void fitsTheColumn() {
        String hash = ContentHash.of("anything".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }
}
