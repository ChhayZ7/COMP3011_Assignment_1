package comp3011.assignment1.error;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import comp3011.assignment1.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Central translation of exceptions into the ErrorResponse shape defined by the YAML
 * specification.
 *
 * Handling errors in one advice rather than per controller keeps controllers free of
 * try/catch noise and guarantees that every failure path emits the same JSON contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // A second shutdown request while one is already running is a conflict, not an error.
    @ExceptionHandler(ShutdownInProgressException.class)
    public ResponseEntity<ErrorResponse> handleShutdownInProgress(ShutdownInProgressException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // Upstream STT failures surface to the client as 500 with a generic message.
    @ExceptionHandler(TranscriptionException.class)
    public ResponseEntity<ErrorResponse> handleTranscriptionFailure(TranscriptionException ex, HttpServletRequest request) {
        log.error("Transcription failed for {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "The speech-to-text service could not process the request.", request);
    }

    /**
     * Catch-all for anything unanticipated.
     *
     * The client is deliberately given a fixed message rather than {@code ex.getMessage()}.
     * Exception text originating from an HTTP client library can contain request details,
     * including authorisation headers, so echoing it would be a credential disclosure path.
     * The full stack trace is logged locally where it is useful and not client-visible.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
