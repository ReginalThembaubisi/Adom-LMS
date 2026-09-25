package com.example.learnerassignments.model;

/**
 * Where a learnership's public advert stands. Only OPEN adverts appear on the website, and
 * only while their closing date has not passed. A learnership that is only used to organise
 * existing learners inside the LMS can stay DRAFT forever.
 */
public enum LearnershipStatus {
    DRAFT,
    OPEN,
    CLOSED
}
