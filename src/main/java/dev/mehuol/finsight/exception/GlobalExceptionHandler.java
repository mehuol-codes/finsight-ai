package dev.mehuol.finsight.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions from any controller to a JSON body {"error": "..."} that the UI displays. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UploadInProgressException.class)
    public ResponseEntity<Map<String, String>> conflict(UploadInProgressException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MarketDataException.class)
    public ResponseEntity<Map<String, String>> badGateway(MarketDataException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", String.valueOf(message)));
    }
}
