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
    FAILED
}
