package com.example.learnerassignments.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every exception type this application deliberately throws with a message meant for a client
 * to read gets an explicit handler here, at the status that describes what actually happened.
 * The one thing that must never happen again is what this class has already done three times:
 * catch something specific inside the generic {@link #handleGenericException} fallback and
 * answer 500 for a condition that was never a server fault.
 *
 * <ul>
 *   <li>{@code DataIntegrityViolationException} answered "can't delete, still referenced" for
 *       a duplicate-key violation on an insert — misleading a facilitator recovering from a
 *       database restore into thinking they were deleting something.</li>
 *   <li>{@code AccessDeniedException} (Phase 8) fell through to this class's own catch-all and
 *       came back 500 instead of 403 — Spring MVC resolves {@code @ExceptionHandler}s before an
 *       exception can ever reach the security filter chain that would otherwise have turned it
 *       into a 403 on its own, so having a catch-all here at all pre-empts that translation.</li>
 *   <li>{@code LecturerController} threw bare, untyped {@code RuntimeException} for "not
 *       authenticated" and "not found" — the one controller in the codebase that never adopted
 *       {@link ResourceNotFoundException}, so seven call sites answered 500 for what elsewhere
 *       in this app is a routine 404 (Phase 9 exception-handling audit).</li>
 * </ul>
 *
 * <p>The pattern behind all three: a specific, well-understood failure reached this class with
 * no handler of its own, so the generic path decided its status — and the generic path is where
 * "unexpected" quietly comes to mean "500", whatever the exception actually was. The audit that
 * found the third of these went looking for others rather than adding a fourth named handler
 * and calling it done: {@link IllegalStateException} is now handled explicitly too, and the
 * true catch-all no longer echoes an arbitrary exception's message to the client at all — see
 * both below for why.
 */
@RestControllerAdvice
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * A permission gate that is not a record-existence check — the PoE export's admin-only
     * scopes (Phase 8) are the first caller.
     *
     * Without this, {@code AccessDeniedException} fell through to the catch-all below and came
     * back as a 500, discovered against a running container rather than a unit test: MVC's
     * {@code @ExceptionHandler} resolution handles the exception before it can ever reach
     * Spring Security's {@code ExceptionTranslationFilter}, so the textbook "throw it and the
     * filter chain turns it into a 403" behaviour never applied inside a
     * {@code @RestControllerAdvice} that already has a catch-all registered.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Forbidden");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // A learner endpoint reached without a usable session. Distinct from the filter-chain
    // 401 because the request did pass the authorization rules — the token expired or was
    // revoked between the security check and the controller.
    @ExceptionHandler(com.example.learnerassignments.security.CurrentLearner.NotAuthenticatedException.class)
    public ResponseEntity<Map<String, Object>> handleNotAuthenticated(
            com.example.learnerassignments.security.CurrentLearner.NotAuthenticatedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", "Authentication required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(InvalidFileException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        body.put("errors", fieldErrors);

        String combinedMessage = fieldErrors.values().stream().collect(Collectors.joining(", "));
        body.put("message", combinedMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "File Size Limit Exceeded");
        body.put("message", "Maximum upload size exceeded. Maximum allowed size is 20MB.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * A precondition on the server's own state, not on what the caller sent —
     * {@code IllegalArgumentException}'s companion, one status class up. Every throw site of
     * this type in the codebase was audited when this handler was added (Cloudinary or file
     * storage unconfigured, SHA-256 unavailable): each is a hand-written, deliberately safe
     * sentence describing a real server condition, so its message is safe to show as written.
     * That audit is also why this type gets its own handler instead of falling through to
     * {@link #handleGenericException}: the fallback below no longer trusts any exception's
     * message by default, precisely so a future throw site doesn't have to be remembered and
     * re-audited by hand the next time someone reads this class.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "The server could not complete that request.");
        log.error("Server precondition failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Database constraint violations, told apart rather than lumped together.
     *
     * This used to answer every violation with "This item can't be deleted because other
     * records still depend on it" — including a duplicate-key violation on an insert, where
     * nothing was being deleted and no dependent records existed. The moment that matters is
     * a restore: learner_code_sequences comes back with the backup, and if it is behind the
     * learners table, the next self-registration collides and the learner is told their item
     * cannot be deleted. That is a misleading signal during a recovery, which is exactly when
     * clear ones are needed.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String detail = rootCauseText(ex);
        // Never echoed to the client — this can contain a column or constraint name that means
        // nothing to whoever hit it, and occasionally the values that violated it. It has to
        // land somewhere, though: this handler used to answer a safe generic message and log
        // nothing at all, which meant the *only* record of what actually broke was gone the
        // moment the response went out. Found while investigating a report that rejecting a
        // document "saved nothing" — the real cause (a notification write sharing the review's
        // transaction) was invisible until this line existed to catch the next occurrence.
        log.error("Data integrity violation: {}", detail, ex);

        String message;
        if (isDuplicateViolation(ex, detail)) {
            message = detail.toLowerCase().contains("learner_code")
                    // Named because the cause is almost never the learner and never obvious:
                    // the code is allocated from a sequence table, and a restore can bring
                    // that table back behind the learners it is meant to number.
                    ? "That learner code is already taken. This usually means "
                      + "learner_code_sequences is behind the learners table — after a database "
                      + "restore it needs resyncing to the highest existing code before anyone "
                      + "registers."
                    : "Something with those details already exists. Check for a duplicate before retrying.";
        } else if (isForeignKeyViolation(ex, detail)) {
            message = "This item can't be deleted because other records still depend on it. "
                    + "Remove or reassign those first.";
        } else {
            message = "The database rejected that change because it would leave the data "
                    + "inconsistent. Nothing was saved.";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private boolean isDuplicateViolation(DataIntegrityViolationException ex, String detail) {
        if (ex instanceof org.springframework.dao.DuplicateKeyException) {
            return true;
        }
        // SQLState 23505 is a unique violation on H2 and PostgreSQL; MySQL reports 23000 for
        // integrity violations generally and distinguishes duplicates by message.
        if ("23505".equals(sqlStateOf(ex))) {
            return true;
        }
        String lower = detail.toLowerCase();
        return lower.contains("duplicate entry")
                || lower.contains("duplicate key")
                || lower.contains("unique constraint")
                || lower.contains("unique index");
    }

    private boolean isForeignKeyViolation(DataIntegrityViolationException ex, String detail) {
        if ("23503".equals(sqlStateOf(ex))) {
            return true;
        }
        String lower = detail.toLowerCase();
        return lower.contains("foreign key") || lower.contains("referential integrity");
    }

    private String sqlStateOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException) {
                return sqlException.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private String rootCauseText(Throwable ex) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                text.append(cause.getMessage()).append(' ');
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return text.toString();
    }

    /**
     * Anything not handled above.
     *
     * Spring's own MVC exceptions are let through with the status they already carry. Without
     * that, this catch-all turned every one of them into a 500: a missing request parameter, a
     * wrong HTTP method, an unsupported content type — all client mistakes — came back as
     * "Internal Server Error". That is wrong twice over. The caller cannot tell they sent a bad
     * request, and nobody watching error rates can tell a real fault from someone's typo.
     *
     * <p><strong>Past this point, nothing is a type this codebase deliberately throws with a
     * client-facing message in mind.</strong> Every one of those has its own handler above, each
     * one added because something specific landed here first and came back as an unhelpful (or
     * actively misleading) 500. This handler used to echo {@code ex.getMessage()} regardless of
     * exception type — safe for the handful of hand-audited throw sites that motivated it, but
     * an open door for the next unaudited one: a raw {@code NullPointerException}, an unwrapped
     * driver exception, a library surprise, any of which can carry an internal detail nobody
     * meant to expose. Rather than adding a fourth named handler for whatever Phase 9 needed and
     * leaving that door open, the door is shut: this path never shows the caller the exception's
     * own message, on any exception type, ever. The server log gets the real one.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());

        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            // Spring's own typed exceptions (bad parameters, wrong method, unsupported media
            // type) are safe to echo — their messages are framework-authored, not application
            // data, and never carry anything this app itself considers sensitive.
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            body.put("status", status.value());
            body.put("error", status.getReasonPhrase());
            body.put("message", ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase());
            return ResponseEntity.status(status).body(body);
        }

        // A genuine, unexpected failure — the one case this class cannot name in advance.
        // Logged in full here because nothing else will; the client gets a fixed, safe message
        // regardless of what this exception's own message says.
        log.error("Unhandled exception", ex);

        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
