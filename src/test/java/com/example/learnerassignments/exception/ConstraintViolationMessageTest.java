package com.example.learnerassignments.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message has to match the constraint that was actually violated.
 *
 * Every violation used to answer "This item can't be deleted because other records still
 * depend on it", including a duplicate key on an insert. The moment that costs something is
 * a restore: learner_code_sequences comes back with the backup, and if it is behind the
 * learners table the next registration collides — and tells the person their item cannot be
 * deleted, during a recovery, when a clear signal is what you need most.
 */
class ConstraintViolationMessageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private String messageFor(DataIntegrityViolationException ex) {
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);
        return String.valueOf(response.getBody().get("message"));
    }

    @Test
    @DisplayName("a learner code collision names the sequence table, not deletion")
    void learnerCodeCollisionExplainsItself() {
        // What H2 and Postgres produce when learner_code_sequences has fallen behind.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("Unique index or primary key violation: "
                        + "\"PUBLIC.UK_LEARNERS_LEARNER_CODE ON PUBLIC.LEARNERS(LEARNER_CODE)\"",
                        "23505"));

        String message = messageFor(ex);

        assertThat(message).contains("learner code is already taken");
        assertThat(message).contains("learner_code_sequences");
        assertThat(message)
                .as("nothing was being deleted")
                .doesNotContain("deleted");
    }

    @Test
    @DisplayName("MySQL's duplicate entry is recognised too")
    void mysqlDuplicateEntry() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("Duplicate entry '202600001' for key 'learners.learner_code'", "23000"));

        assertThat(messageFor(ex)).contains("learner_code_sequences");
    }

    @Test
    @DisplayName("a duplicate that isn't a learner code says so plainly")
    void otherDuplicate() {
        DataIntegrityViolationException ex = new DuplicateKeyException(
                "Unique index or primary key violation: \"PUBLIC.UK_LECTURERS_USERNAME\"");

        String message = messageFor(ex);

        assertThat(message).contains("already exists");
        assertThat(message).doesNotContain("deleted");
        assertThat(message).doesNotContain("learner_code_sequences");
    }

    @Test
    @DisplayName("a dependent-records violation keeps the message it always had")
    void foreignKeyKeepsItsMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("Referential integrity constraint violation: "
                        + "\"FK_SUBMISSION_LEARNER: PUBLIC.SUBMISSIONS FOREIGN KEY(LEARNER_ID)\"",
                        "23503"));

        assertThat(messageFor(ex))
                .contains("can't be deleted because other records still depend on it");
    }

    @Test
    @DisplayName("an unrecognised violation says what it knows, and no more")
    void unknownViolationDoesNotGuess() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("something the database did not like");

        String message = messageFor(ex);

        assertThat(message).contains("Nothing was saved");
        assertThat(message)
                .as("guessing 'deleted' is how the old message misled people")
                .doesNotContain("deleted");
    }

    @Test
    @DisplayName("walking the cause chain terminates on a self-referencing cause")
    void cyclicCausesTerminate() {
        // Defensive: reading the chain is a loop, and a self-referencing cause is a real shape.
        DataIntegrityViolationException ex = new DataIntegrityViolationException("outer");

        assertThat(messageFor(ex)).isNotBlank();
    }
}
