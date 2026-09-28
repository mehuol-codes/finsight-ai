package dev.mehuol.finsight.dto;

/**
 * A document as shown in the UI. {@code embedded} and {@code message} matter while the upload
 * is processing or has failed.
 */
public record DocumentInfo(String name, int chunks, String status, int embedded, String message) {

    public static final String READY = "ready";
    public static final String PROCESSING = "processing";
    public static final String FAILED = "failed";

    public static DocumentInfo ready(String name, int chunks) {
        return new DocumentInfo(name, chunks, READY, chunks, null);
    }
}
