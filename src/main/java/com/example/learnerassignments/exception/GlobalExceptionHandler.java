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
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());

        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            body.put("status", status.value());
            body.put("error", status.getReasonPhrase());
            body.put("message", ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase());
            return ResponseEntity.status(status).body(body);
        }

        // A genuine, unexpected failure. Logged here because nothing else was logging it —
        // which is why this handler quietly answering 500 went unnoticed for so long.
        log.error("Unhandled exception", ex);

        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
