package com.example.learnerassignments.model;

/** Where an export job has got to. */
public enum ExportJobStatus {
    /** Created, not yet picked up by the worker. */
    QUEUED,
    /** The worker is fetching files and building the zip. */
    RUNNING,
    /** The zip exists and can be downloaded. */
    COMPLETED,
    /** Something failed. {@code error} says what, without ever naming a file's storage detail. */
    FAILED,
    /**
     * Completed once, but the file no longer exists — this deployment's disk is ephemeral and
     * does not survive an application restart. Detected at boot; {@code error} tells the
     * requester to ask again rather than leaving them a download link that would 404.
     */
    EXPIRED
}
