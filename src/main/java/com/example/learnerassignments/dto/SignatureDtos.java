package com.example.learnerassignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** What the signing flow and the public verification page send and read back. */
public class SignatureDtos {

    /** What a learner sends to sign one of their own submissions or documents. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SignRequest {
        /** SUBMISSION or LEARNER_DOCUMENT. */
        private String signableType;
        private Long signableId;
        /** The drawn specimen, as a base64 PNG data URI. */
        private String specimenImage;
        /** Re-authentication: the learner's own current password. */
        private String password;
    }

    /**
     * The learner's own view of a signature they placed. Deliberately richer than
     * {@link VerifyResponse} — this is returned only to the authenticated signer themselves,
     * never to the public endpoint.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SignatureResponse {
        private Long id;
        private String signableType;
        private Long signableId;
        private String signerRole;
        private LocalDateTime signedAt;
        private String verificationCode;
        private boolean revoked;
        private LocalDateTime revokedAt;
        private String revokedReason;
        /** True once the async certificate worker has produced a downloadable PDF. */
        private boolean certificateReady;
    }

    /**
     * What {@code GET /api/verify/{code}} returns. Publicly reachable, so this is the whole
     * contract for what a stranger with the code may learn: document type, signer role,
     * signature date, and whether the hash still matches. Nothing else — no learner name, no ID
     * number, no file content, not even the internal signable id.
     */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerifyResponse {
        private String documentType;
        private String signerRole;
        private LocalDateTime signedAt;
        private boolean hashMatches;
        private boolean revoked;
        /** hashMatches && !revoked — the one-glance answer. */
        private boolean valid;
    }
}
