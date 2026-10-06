package dev.mehuol.finsight.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.genai.errors.ApiException;

class AiErrorsTest {

    private static RuntimeException wrapped(int code, String message) {
        // Spring AI wraps provider errors, sometimes more than once.
        return new IllegalStateException("Stream processing failed",
                new RuntimeException("Failed to generate content", new ApiException(code, "STATUS", message)));
    }

    @Test
    void explainsRateLimits() {
        RuntimeException e = wrapped(429, "You exceeded your current quota");
        assertTrue(AiErrors.isRateLimited(e));
        assertTrue(AiErrors.userMessage(e).startsWith("The Gemini free-tier limit was reached"));
    }

    @Test
    void explainsKeyModelAndOutageErrors() {
        assertTrue(AiErrors.userMessage(wrapped(403, "denied")).contains("GEMINI_API_KEY"));
        assertTrue(AiErrors.userMessage(wrapped(404, "models/x is not found")).contains("models/x is not found"));
        assertTrue(AiErrors.userMessage(wrapped(503, "overloaded")).contains("temporarily unavailable"));
        assertFalse(AiErrors.isRateLimited(wrapped(503, "overloaded")));
    }

    @Test
    void fallsBackToTheRootCauseForOtherErrors() {
        RuntimeException e = new RuntimeException("outer", new IllegalArgumentException("Document not found: a.pdf"));
        assertEquals("Document not found: a.pdf", AiErrors.userMessage(e));
    }
}
