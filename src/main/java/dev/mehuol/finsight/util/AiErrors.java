package dev.mehuol.finsight.util;

import com.google.genai.errors.ApiException;

/** Turns Gemini API failures into messages a user can act on, instead of raw provider errors. */
public final class AiErrors {

    private AiErrors() {
    }

    /** The Gemini error in the cause chain, if any. */
    public static ApiException apiException(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                return api;
            }
        }
        return null;
    }

    public static boolean isRateLimited(Throwable e) {
        ApiException api = apiException(e);
        return api != null && api.code() == 429;
    }

    public static String userMessage(Throwable e) {
        ApiException api = apiException(e);
        if (api != null) {
            switch (api.code()) {
                case 429:
                    return "The Gemini free-tier limit was reached. Wait a minute and try again; if it keeps "
                            + "happening, today's quota may be used up.";
                case 401, 403:
                    return "The Gemini API key is missing or invalid. Check the GEMINI_API_KEY environment variable.";
                case 404:
                    return "The configured Gemini model is not available. Set GEMINI_CHAT_MODEL to a current model. ("
                            + api.message() + ")";
                case 500, 502, 503, 504:
                    return "Gemini is temporarily unavailable. Please try again in a moment.";
                default:
                    return "Gemini returned an error: " + api.message();
            }
        }
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
